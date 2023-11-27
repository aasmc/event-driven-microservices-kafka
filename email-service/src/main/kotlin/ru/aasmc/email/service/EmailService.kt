package ru.aasmc.email.service

import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.processor.TopicNameExtractor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.aasmc.avro.eventdriven.Customer
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.OrderEnriched
import ru.aasmc.avro.eventdriven.Payment
import ru.aasmc.email.dto.EmailTuple
import ru.aasmc.eventdriven.common.schemas.Schemas
import java.time.Duration

/**
 * A very simple service which sends emails. Order and Payment streams are joined
 * using a window. The result is then joined to a lookup table of Customers.
 * Finally an email is sent for each resulting tuple.
 */
@Service
class EmailService(
    private val schemas: Schemas,
    private val ordersCustomers: KStream<String, OrderEnriched>
) {

    @Autowired
    fun processStreams(builder: StreamsBuilder) {
        //Send the order to a topic whose name is the value of customer level
        ordersCustomers
            //TopicNameExtractor to get the topic name (i.e., customerLevel) from the enriched order record being sent
            .to(
                TopicNameExtractor { orderId, orderEnriched, record ->
                    orderEnriched.customerLevel
                },
                Produced.with(schemas.ORDERS_ENRICHED.keySerde, schemas.ORDERS_ENRICHED.valueSerde)
            )
    }

}