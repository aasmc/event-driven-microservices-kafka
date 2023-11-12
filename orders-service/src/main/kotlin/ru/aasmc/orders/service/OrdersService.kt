package ru.aasmc.orders.service

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.errors.InvalidStateStoreException
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.apache.kafka.streams.state.StreamsMetadata.NOT_AVAILABLE
import org.slf4j.LoggerFactory
import org.springframework.kafka.config.StreamsBuilderFactoryBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.web.context.request.async.DeferredResult
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.OrderState
import ru.aasmc.eventdriven.common.util.ServiceUtils
import ru.aasmc.orders.config.props.OrdersProps
import ru.aasmc.orders.dto.OrderDto
import ru.aasmc.orders.error.NotFoundException
import ru.aasmc.orders.mapper.OrderMapper
import ru.aasmc.orders.utils.FilteredResponse
import ru.aasmc.orders.utils.HostStoreInfo
import ru.aasmc.orders.utils.Paths
import java.util.concurrent.CompletableFuture


private val log = LoggerFactory.getLogger(OrdersService::class.java)
const val CALL_TIMEOUT = 10000L

/**
 * This class provides a REST interface to write and read orders using a CQRS pattern
 * (https://martinfowler.com/bliki/CQRS.html). Three methods are exposed over HTTP:
 * <p>
 * - POST(Order) -> Writes and order and returns location of the resource.
 * <p>
 * - GET(OrderId) (Optional timeout) -> Returns requested order, blocking for timeout if no id present.
 * <p>
 * - GET(OrderId)/Validated (Optional timeout)
 * <p>
 * POST does what you might expect: it adds an Order to the system returning when Kafka sends the appropriate
 * acknowledgement.
 * <p>
 * GET accesses an inbuilt Materialized View, of Orders, which are kept in a
 * State Store inside the service. This CQRS-styled view is updated asynchronously wrt the HTTP
 * POST.
 * <p>
 * Calling GET(id) when the ID is not present will block the caller until either the order
 * is added to the view, or the passed TIMEOUT period elapses. This allows the caller to
 * read-their-own-writes.
 * <p>
 * In addition HTTP POST returns the location of the order submitted in the response.
 * <p>
 * Calling GET/id/validated will block until the FAILED/VALIDATED order is available in
 * the View.
 * <p>
 * The View can also be scaled out linearly simply by adding more instances of the
 * view service, and requests to any of the REST endpoints will be automatically forwarded to the
 * correct instance for the key requested orderId via Kafka's Queryable State feature.
 * <p>
 * Non-blocking IO is used for all operations other than the intialization of state stores on
 * startup or rebalance which will block calling Jetty thread.
 *<p>
 * NB This demo code only includes a partial implementation of the holding of outstanding requests
 * and as such would lead timeouts if used in a production use case.
 */
@Service
class OrdersService(
    private val factoryBean: StreamsBuilderFactoryBean,
    private val metadataService: MetadataService,
    private val producer: KafkaTemplate<String, Order>,
    private val serviceUtils: ServiceUtils,
    private val ordersProps: OrdersProps,
    private val mapper: OrderMapper,
    private val webClientBuilder: WebClient.Builder,
    private val outstandingRequests: MutableMap<String, FilteredResponse<String, Order, OrderDto>>
) {

    private lateinit var orderStore: ReadOnlyKeyValueStore<String, Order>

    fun initOrderStore() {
        if (!::orderStore.isInitialized) {
            val streams = factoryBean.kafkaStreams
                ?: throw RuntimeException("Cannot obtain KafkaStreams instance.")
            orderStore = streams.store(
                StoreQueryParameters.fromNameAndType(
                    ordersProps.storeName,
                    QueryableStoreTypes.keyValueStore()
                )
            )
        }
    }

    fun getOrderDto(id: String, asyncResponse: DeferredResult<OrderDto>, timeout: Long) {
        initOrderStore()
        CompletableFuture.runAsync {
            val hostForKey = getKeyLocationOrBlock(id, asyncResponse)
                ?: return@runAsync //request timed out so return

            if (thisHost(hostForKey)) {
                fetchLocal(id, asyncResponse) { _, _ -> true }
            } else {
                val path = Paths(hostForKey.host, hostForKey.port).urlGet(id)
                fetchFromOtherHost(path, asyncResponse, timeout)
            }
        }
    }

    fun submitOrder(dto: OrderDto, asyncResponse: DeferredResult<String>) {
        initOrderStore()
        val order = mapper.toDomain(dto)
        val res = producer.send(ordersProps.topic, order.id, order)
        res.whenComplete { sendResult, ex ->
            if (ex != null) {
                asyncResponse.setErrorResult(ex)
            } else {
                val uri = "/v1/orders/${sendResult.producerRecord.value().id}"
                asyncResponse.setResult(uri)
            }
        }
    }

    fun getValidatedOrder(
        id: String,
        asyncResponse: DeferredResult<OrderDto>,
        timeout: Long
    ) {
        initOrderStore()
        CompletableFuture.runAsync {
            val hostForKey = getKeyLocationOrBlock(id, asyncResponse)
                ?: return@runAsync
            if (thisHost(hostForKey)) {
                fetchLocal(id, asyncResponse) { _, order ->
                    order.state == OrderState.VALIDATED || order.state == OrderState.FAILED
                }
            } else {
                val path = Paths(hostForKey.host, hostForKey.port).urlGetValidated(id)
                fetchFromOtherHost(path, asyncResponse, timeout)
            }
        }

    }

    /**
     * Fetch the order from the local materialized view
     *
     * @param id ID to fetch
     * @param predicate a filter that for this fetch, so for example we might fetch only VALIDATED
     * orders.
     */
    private fun fetchLocal(
        id: String,
        asyncResponse: DeferredResult<OrderDto>,
        predicate: (String, Order) -> Boolean
    ) {
        log.info("running GET on this node")
        try {
            val order = orderStore.get(id)
            if (order == null || !predicate(id, order)) {
                log.info("Delaying get as order not present for id $id")
                outstandingRequests[id] = FilteredResponse(
                    asyncResponse = asyncResponse,
                    predicate = predicate
                )
            }
            asyncResponse.setResult(mapper.toDto(order))
        } catch (e: InvalidStateStoreException) {
            log.error("Exception while querying local state store. {}", e.message)
            outstandingRequests[id] = FilteredResponse(asyncResponse, predicate)
        }
    }

    private fun fetchFromOtherHost(
        path: String,
        asyncResponse: DeferredResult<OrderDto>,
        timeout: Long
    ) {
        log.info("Chaining GET to a different instance: {}", path)
        try {
            val order = webClientBuilder.build()
                .get()
                .uri("$path?timeout=$timeout")
                .retrieve()
                .bodyToMono<OrderDto>()
                .block() ?: throw NotFoundException("FetchFromOtherHost returned null for request: $path")
            asyncResponse.setResult(order)
        } catch (swallowed: Exception) {
            log.warn("FetchFromOtherHost failed.", swallowed)
        }
    }


    /**
     * Use Kafka Streams' Queryable State API to work out if a key/value pair is located on
     * this node, or on another Kafka Streams node. This returned HostStoreInfo can be used
     * to redirect an HTTP request to the node that has the data.
     * <p>
     * If metadata is available, which can happen on startup, or during a rebalance, block until it is.
     */
    private fun getKeyLocationOrBlock(id: String, asyncResponse: DeferredResult<OrderDto>): HostStoreInfo? {
        var locationOfKey = getHostForOrderId(id)
        while (locationMetadataIsUnavailable(locationOfKey)) {
            if (asyncResponse.isSetOrExpired) {
                return null
            }
            try {
                Thread.sleep(minOf(CALL_TIMEOUT, 200))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            locationOfKey = getHostForOrderId(id)
        }
        return locationOfKey
    }

    private fun locationMetadataIsUnavailable(hostWithKey: HostStoreInfo): Boolean {
        return NOT_AVAILABLE.host() == hostWithKey.host
                && NOT_AVAILABLE.port() == hostWithKey.port
    }

    private fun thisHost(host: HostStoreInfo): Boolean {
        return host.host == serviceUtils.getHost() &&
                host.port == serviceUtils.getPort()
    }

    private fun getHostForOrderId(orderId: String): HostStoreInfo {
        return metadataService
            .streamsMetadataForStoreAndKey(ordersProps.storeName, orderId, Serdes.String().serializer())
    }
}