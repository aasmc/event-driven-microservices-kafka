package ru.aasmc.orders.service

import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Materialized
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.eventdriven.common.schemas.Schemas
import ru.aasmc.orders.config.props.OrdersProps
import ru.aasmc.orders.dto.OrderDto
import ru.aasmc.orders.mapper.OrderMapper
import ru.aasmc.orders.utils.FilteredResponse

private val log = LoggerFactory.getLogger(OrdersServiceTopology::class.java)

@Component
class OrdersServiceTopology(
    private val orderProps: OrdersProps,
    private val mapper: OrderMapper,
    private val schemas: Schemas,
    private val outstandingRequests: MutableMap<String, FilteredResponse<String, Order, OrderDto>>
) {

    @Autowired
    fun ordersTableTopology(builder: StreamsBuilder) {
        log.info("Calling OrdersServiceTopology.ordersTableTopology()")
        builder.table(
            orderProps.topic,
            Consumed.with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde),
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