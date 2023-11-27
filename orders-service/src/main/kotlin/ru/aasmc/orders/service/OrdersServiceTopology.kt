package ru.aasmc.orders.service

import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KTable
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.orders.dto.OrderDto
import ru.aasmc.orders.mapper.OrderMapper
import ru.aasmc.orders.utils.FilteredResponse

private val log = LoggerFactory.getLogger(OrdersServiceTopology::class.java)

@Component
class OrdersServiceTopology(
    private val mapper: OrderMapper,
    private val outstandingRequests: MutableMap<String, FilteredResponse<String, Order, OrderDto>>,
    private val ordersTable: KTable<String, Order>
) {

    @Autowired
    fun ordersTableTopology(builder: StreamsBuilder) {
        log.info("Calling OrdersServiceTopology.ordersTableTopology()")
        ordersTable.toStream().foreach(::maybeCompleteLongPollGet)
    }

    private fun maybeCompleteLongPollGet(id: String, order: Order) {
        val callback = outstandingRequests[id]
        if (callback?.asyncResponse?.isSetOrExpired == true) {
            outstandingRequests.remove(id)
        } else if (callback != null && callback.predicate(id, order)) {
            callback.asyncResponse.setResult(mapper.toDto(order))
            outstandingRequests.remove(id)
        }
    }
}