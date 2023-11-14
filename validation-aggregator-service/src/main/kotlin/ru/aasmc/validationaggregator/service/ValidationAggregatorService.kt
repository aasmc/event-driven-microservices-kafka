package ru.aasmc.validationaggregator.service

import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.OrderState
import ru.aasmc.avro.eventdriven.OrderValidation
import ru.aasmc.avro.eventdriven.OrderValidationResult.FAIL
import ru.aasmc.avro.eventdriven.OrderValidationResult.PASS
import ru.aasmc.eventdriven.common.schemas.Schemas
import java.time.Duration

private val log = LoggerFactory.getLogger(ValidationAggregatorService::class.java)

/**
 * A simple service which listens to validation results from each of the Validation
 * services and aggregates them by order Id, triggering a pass or fail based on whether
 * all rules pass or not.
 */
@Service
class ValidationAggregatorService(
    private val schemas: Schemas
) {

    private val serdes1: Consumed<String, OrderValidation> = Consumed
        .with(schemas.ORDER_VALIDATIONS.keySerde, schemas.ORDER_VALIDATIONS.valueSerde)

    private val serdes2: Consumed<String, Order> = Consumed
        .with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde)

    private val serdes3: Grouped<String, OrderValidation> = Grouped
        .with(schemas.ORDER_VALIDATIONS.keySerde, schemas.ORDER_VALIDATIONS.valueSerde)

    private val serdes4: StreamJoined<String, Long, Order> = StreamJoined
        .with(schemas.ORDERS.keySerde, Serdes.Long(), schemas.ORDERS.valueSerde)

    private val serdes5: Produced<String, Order> = Produced
        .with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde)

    private val serdes6: Grouped<String, Order> = Grouped
        .with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde)

    private val serdes7: StreamJoined<String, OrderValidation, Order> = StreamJoined
        .with(schemas.ORDERS.keySerde, schemas.ORDER_VALIDATIONS.valueSerde, schemas.ORDERS.valueSerde)


    @Autowired
    fun aggregateOrderValidations(builder: StreamsBuilder) {
        val numberOfRules = 3

        val validations: KStream<String, OrderValidation> = builder
            .stream(schemas.ORDER_VALIDATIONS.name, serdes1)

        val orders = builder
            .stream(schemas.ORDERS.name, serdes2)
            .filter { id, order -> OrderState.CREATED == order.state }

        // if all rules pass then validate the order
        validations
            .groupByKey(serdes3)
            .windowedBy(SessionWindows.ofInactivityGapWithNoGrace(Duration.ofMinutes(5)))
            .aggregate(
                { 0L },
                { id, result, total ->
                    if (PASS == result.validationResult) total + 1 else total
                },
                { k, a, b ->
                    b ?: a //include a merger as we're using session windows.
                },
                Materialized.with(null, Serdes.Long())
            )
            // get rid of window
            .toStream { windowedKey, total -> windowedKey.key() }
            //When elements are evicted from a session window they create delete events. Filter these.
            .filter { _, v -> v != null }
            //only include results were all rules passed validation
            .filter { _, total -> total >= numberOfRules }
            // join back orders
            .join(
                orders,
                { id, order ->
                    //Set the order to Validated
                    Order.newBuilder(order).setState(OrderState.VALIDATED).build()
                },
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMinutes(5)),
                serdes4
            )
            //Push the validated order into the orders topic
            .to(schemas.ORDERS.name, serdes5)
        //If any rule fails then fail the order
        validations.filter { id, rule ->
            FAIL == rule.validationResult
        }
            .join(
                orders,
                { id, order ->
                    Order.newBuilder(order).setState(OrderState.FAILED).build()
                },
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMinutes(5)),
                serdes7
            )
            //there could be multiple failed rules for each order so collapse to a single order
            .groupByKey(serdes6)
            .reduce { order, v1 -> order }
            //Push the validated order into the orders topic
            .toStream()
            .to(
                schemas.ORDERS.name,
                Produced.with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde)
            )
    }
}