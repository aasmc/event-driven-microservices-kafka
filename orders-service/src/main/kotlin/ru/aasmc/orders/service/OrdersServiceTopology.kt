package ru.aasmc.orders.service

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Materialized
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.orders.config.props.KafkaProps
import ru.aasmc.orders.config.props.OrdersProps
import ru.aasmc.orders.dto.OrderDto
import ru.aasmc.orders.mapper.OrderMapper
import ru.aasmc.orders.utils.FilteredResponse

private val log = LoggerFactory.getLogger(OrdersServiceTopology::class.java)

@Component
class OrdersServiceTopology(
    private val orderProps: OrdersProps,
    private val kafkaProps: KafkaProps,
    private val mapper: OrderMapper,
    private val outstandingRequests: MutableMap<String, FilteredResponse<String, Order, OrderDto>>
) {

    @Autowired
    fun ordersTableTopology(builder: StreamsBuilder) {
        log.info("Calling OrdersServiceTopology.ordersTableTopology()")
        val serdeConfig = hashMapOf<String, Any>(
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaProps.schemaRegistryUrl
        )
        val orderSerde = SpecificAvroSerde<Order>()
        orderSerde.configure(serdeConfig, false)
        builder.table(
            orderProps.topic,
            Consumed.with(Serdes.String(), orderSerde),
            Materialized.`as`(orderProps.storeName)
        ).toStream().foreach(::maybeCompleteLongPollGet)
    }

    private fun maybeCompleteLongPollGet(id: String, order: Order) {
        val callback = outstandingRequests[id]
        if (callback != null && callback.predicate(id, order)) {
            callback.asyncResponse.setResult(mapper.toDto(order))
        }
    }
}