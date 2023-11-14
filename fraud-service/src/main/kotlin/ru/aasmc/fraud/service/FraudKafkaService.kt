package ru.aasmc.fraud.service

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.aasmc.avro.eventdriven.*
import ru.aasmc.eventdriven.common.props.KafkaProps
import ru.aasmc.eventdriven.common.schemas.Schemas
import java.time.Duration

private val log = LoggerFactory.getLogger(FraudKafkaService::class.java)

private const val FRAUD_LIMIT = 2000

/**
 * This service searches for potentially fraudulent transactions by calculating the total value of
 * orders for a customer within a time period, then checks to see if this is over a configured
 * limit. <p> i.e. if(SUM(order.value, 5Mins) > $5000) GroupBy customer -> Fail(orderId) else
 * Pass(orderId)
 */
@Service
class FraudKafkaService(
    private val schemas: Schemas
) {

    @Autowired
    fun processStreams(builder: StreamsBuilder) {
        //Latch onto instances of the orders and inventory topics

        val orders: KStream<String, Order> = builder
            .stream(
                schemas.ORDERS.name,
                Consumed.with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde)
            )
            .filter { _, order -> OrderState.CREATED == order.state }

        //Create an aggregate of the total value by customer and hold it with the order.
        // We use session windows to detect periods of activity.
        val aggregate: KTable<Windowed<Long>, OrderValue> = orders
            // creates a repartition internal topic if the value to be grouped by differs from
            // the key and downstream nodes need the new key
            .groupBy(
                { id, order -> order.customerId },
                Grouped.with(Serdes.Long(), schemas.ORDERS.valueSerde)
            )
            .windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofHours(1)))
            .aggregate(
                ::OrderValue,
                { custId, order, total ->
                    OrderValue(order, total.value + order.quantity * order.price)
                },
                { k, a, b ->
                    simpleMerge(a, b)
                },//include a merger as we're using session windows.,
                Materialized.with(null, schemas.ORDER_VALUE_SERDE)
            )

        //Ditch the windowing and rekey
        val ordersWithTotals: KStream<String, OrderValue> = aggregate
            .toStream { windowedKey, orderValue -> windowedKey.key() }
            //When elements are evicted from a session window they create delete events. Filter these out.
            .filter { k, v -> v != null }
            .selectKey { id, orderValue -> orderValue.order.id }

        //Now branch the stream into two, for pass and fail, based on whether the windowed
        // total is over Fraud Limit
        val forks: Map<String, KStream<String, OrderValue>> = ordersWithTotals
            .split(Named.`as`("limit-"))
            .branch(
                { id, orderValue -> orderValue.value >= FRAUD_LIMIT },
                Branched.`as`("above")
            )
            .branch(
                { id, orderValue -> orderValue.value < FRAUD_LIMIT },
                Branched.`as`("below")
            )
            .noDefaultBranch()

        forks["limit-above"]?.mapValues { orderValue ->
            OrderValidation(
                orderValue.order.id,
                OrderValidationType.FRAUD_CHECK,
                OrderValidationResult.FAIL
            )
        }?.to(
            schemas.ORDER_VALIDATIONS.name,
            Produced.with(
                schemas.ORDER_VALIDATIONS.keySerde,
                schemas.ORDER_VALIDATIONS.valueSerde
            )
        )

        forks["limit-below"]?.mapValues { orderValue ->
            OrderValidation(
                orderValue.order.id,
                OrderValidationType.FRAUD_CHECK,
                OrderValidationResult.PASS
            )
        }?.to(
            schemas.ORDER_VALIDATIONS.name,
            Produced.with(
                schemas.ORDER_VALIDATIONS.keySerde,
                schemas.ORDER_VALIDATIONS.valueSerde
            )
        )
    }

    private fun simpleMerge(a: OrderValue?, b: OrderValue): OrderValue {
        return OrderValue(b.order, (a?.value ?: 0.0) + b.value)
    }

}