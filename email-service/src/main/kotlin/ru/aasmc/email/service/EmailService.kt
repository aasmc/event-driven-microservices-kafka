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

@Service
class EmailService(
    private val emailer: Emailer,
    private val schemas: Schemas
) {

    @Autowired
    fun processStreams(builder: StreamsBuilder) {
        val orders: KStream<String, Order> = builder.stream(
            schemas.ORDERS.name,
            Consumed.with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde)
        )
        //Create the streams/tables for the join
        val payments: KStream<String, Payment> = builder.stream(
            schemas.PAYMENTS.name,
            Consumed.with(schemas.PAYMENTS.keySerde, schemas.PAYMENTS.valueSerde)
        )
            //Rekey payments to be by OrderId for the windowed join
            .selectKey { s, payment -> payment.orderId }

        val customers: GlobalKTable<Long, Customer> = builder.globalTable(
            schemas.CUSTOMERS.name,
            Consumed.with(schemas.CUSTOMERS.keySerde, schemas.CUSTOMERS.valueSerde)
        )

        val serdes: StreamJoined<String, Order, Payment> = StreamJoined
            .with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde, schemas.PAYMENTS.valueSerde)
        //Join the two streams and the table then send an email for each
        orders.join(
            payments,
            ::EmailTuple,
            JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMinutes(1)),
            serdes
        )
            //Next join to the GKTable of Customers
            .join(
                customers,
                { _, value -> value.order.customerId },
                // note how, because we use a GKtable, we can join on any attribute of the Customer.
                EmailTuple::setCustomer
            )
            //Now for each tuple send an email.
            .peek { _, emailTuple ->
                emailer.sendEmail(emailTuple)
            }

        //Send the order to a topic whose name is the value of customer level
        orders.join(
            customers,
            { orderId, order -> order.customerId },
            { order, customer ->
                OrderEnriched(order.id, order.customerId, customer.level)
            }
        )
            //TopicNameExtractor to get the topic name (i.e., customerLevel) from the enriched order record being sent
            .to(
                TopicNameExtractor { orderId, orderEnriched, record ->
                    orderEnriched.customerLevel
                },
                Produced.with(schemas.ORDERS_ENRICHED.keySerde, schemas.ORDERS_ENRICHED.valueSerde)
            )
    }

}