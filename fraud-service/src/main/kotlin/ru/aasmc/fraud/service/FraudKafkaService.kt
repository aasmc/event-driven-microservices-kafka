package ru.aasmc.fraud.service

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Produced
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.aasmc.avro.eventdriven.OrderValidation
import ru.aasmc.avro.eventdriven.OrderValidationResult
import ru.aasmc.avro.eventdriven.OrderValidationType
import ru.aasmc.avro.eventdriven.OrderValue
import ru.aasmc.eventdriven.common.props.TopicsProps
import ru.aasmc.eventdriven.common.schemas.Schemas

private val log = LoggerFactory.getLogger(FraudKafkaService::class.java)

/**
 * This service searches for potentially fraudulent transactions by calculating the total value of
 * orders for a customer within a time period, then checks to see if this is over a configured
 * limit. <p> i.e. if(SUM(order.value, 5Mins) > $2000) GroupBy customer -> Fail(orderId) else
 * Pass(orderId)
 */
@Service
class FraudKafkaService(
    private val schemas: Schemas,
    private val topicProps: TopicsProps,
    private val forks: Map<String, KStream<String, OrderValue>>
) {

    @Autowired
    fun processStreams(builder: StreamsBuilder) {

        val keySerde = schemas.ORDER_VALIDATIONS.keySerde
        val valueSerde = schemas.ORDER_VALIDATIONS.valueSerde
        val config = hashMapOf<String, Any>(
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to topicProps.schemaRegistryUrl,
            AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to false,
            AbstractKafkaSchemaSerDeConfig.USE_LATEST_VERSION to true,
        )
        keySerde.configure(config, true)
        valueSerde.configure(config, false)

        forks["limit-above"]?.mapValues { orderValue ->
            OrderValidation(
                orderValue.order.id,
                OrderValidationType.FRAUD_CHECK,
                OrderValidationResult.FAIL
            )
        }?.peek { key, value ->
            log.info("Sending OrderValidation for failed check in Fraud Service to Kafka. Order: {}", value)
        }?.to(
            schemas.ORDER_VALIDATIONS.name,
            Produced.with(
                keySerde,
                valueSerde
            )
        )

        forks["limit-below"]?.mapValues { orderValue ->
            OrderValidation(
                orderValue.order.id,
                OrderValidationType.FRAUD_CHECK,
                OrderValidationResult.PASS
            )
        }?.peek { key, value ->
            log.info("Sending OrderValidation for passed check in Fraud Service to Kafka. Order: {}", value)
        }?.to(
            schemas.ORDER_VALIDATIONS.name,
            Produced.with(
                schemas.ORDER_VALIDATIONS.keySerde,
                schemas.ORDER_VALIDATIONS.valueSerde
            )
        )
    }

}