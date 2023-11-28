package ru.aasmc.fraud.service

import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import org.apache.kafka.common.serialization.Serdes.StringSerde
import org.apache.kafka.streams.*
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.OrderValidation
import ru.aasmc.eventdriven.common.props.TopicsProps
import ru.aasmc.eventdriven.common.schemas.Schemas
import java.util.*

const val SCHEMA_REGISTRY_SCOPE = "fraud-service-test"
const val ORDERS_TOPIC = "orders.v1"
const val VALIDATION_TOPIC = "order-validations.v1"

class FraudKafkaServiceTest {

    private lateinit var testDriver: TopologyTestDriver
    private lateinit var ordersTopic: TestInputTopic<String, Order>
    private lateinit var orderValidationsTopic: TestOutputTopic<String, OrderValidation>

    private val topicsProps = TopicsProps(
        ordersTopic = ORDERS_TOPIC,
        ordersEnrichedTopic = "",
        inventoryTopic = "",
        orderValidationTopic = VALIDATION_TOPIC,
        paymentsTopic = "",
        customersTopic = "",
        platinumTopic = "",
        goldTopic = "",
        bronzeTopic = "",
        silverTopic = "",
        schemaRegistryUrl = "mock://$SCHEMA_REGISTRY_SCOPE",
        partitions = 1,
        replication = 1
    )
    private val schemas = Schemas(
        topicsProps
    )


    private val fraudService = FraudKafkaService(schemas, topicsProps)

    @BeforeEach
    fun setup() {
        schemas.configureTopics()
        val builder = StreamsBuilder()
        fraudService.processStreams(builder)
        val topology = builder.build()
        val props = Properties()
        props[StreamsConfig.APPLICATION_ID_CONFIG] = "test"
        props[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "dummy:1234"
        props[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = StringSerde::class.java
        props[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = SpecificAvroSerde::class.java
        props[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = topicsProps.schemaRegistryUrl
        testDriver = TopologyTestDriver(topology, props)
        ordersTopic = testDriver.createInputTopic(
            schemas.ORDERS.name,
            schemas.ORDERS.keySerde.serializer(),
            schemas.ORDERS.valueSerde.serializer()
        )

        orderValidationsTopic = testDriver.createOutputTopic(
            schemas.ORDER_VALIDATIONS.name,
            schemas.ORDER_VALIDATIONS.keySerde.deserializer(),
            schemas.ORDER_VALIDATIONS.valueSerde.deserializer()
        )
    }

    @AfterEach
    fun tearDown() {
        testDriver.close()
        MockSchemaRegistry.dropScope(SCHEMA_REGISTRY_SCOPE)
    }


    @Test
    fun shouldValidateWhetherOrderAmountExceedsFraudLimitOverWindow() {
        sendOrders()
        val result = orderValidationsTopic.readValuesToList()
        assertThat(result).isEqualTo(expected)
    }

    private fun sendOrders() {
        orders.forEach { order ->
            ordersTopic.pipeInput(order.id, order)
        }
    }


}