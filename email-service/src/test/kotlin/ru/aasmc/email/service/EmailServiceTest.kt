package ru.aasmc.email.service

import io.confluent.kafka.schemaregistry.testutil.MockSchemaRegistry
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.kstream.KStream
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import ru.aasmc.avro.eventdriven.Customer
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.Payment
import ru.aasmc.email.dto.EmailTuple
import ru.aasmc.eventdriven.common.schemas.Schemas

@SpringBootTest
@ActiveProfiles("integtest")
@Testcontainers
class EmailServiceTest {

    companion object {
        @Container
        @JvmStatic
        private val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.2"))

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("kafkaprops.bootstrapServers") { kafkaContainer.bootstrapServers }
        }

    }

    @Autowired
    private lateinit var customerProducer: KafkaTemplate<Long, Customer>
    @Autowired
    private lateinit var orderProducer: KafkaTemplate<String, Order>
    @Autowired
    private lateinit var paymentProducer: KafkaTemplate<String, Payment>
    @Autowired
    private lateinit var schemas: Schemas
    @Autowired
    private lateinit var ordersPaymentsCustomers: KStream<String, EmailTuple>


    @Test
    fun shouldSendEmailWithValidContents() {
        customerProducer.send(ProducerRecord(schemas.CUSTOMERS.name, customer.id, customer))
        orderProducer.send(ProducerRecord(schemas.ORDERS.name, order.id, order))
        paymentProducer.send(ProducerRecord(schemas.PAYMENTS.name, payment.id, payment))

        ordersPaymentsCustomers.foreach { _, value ->
            assertThat(value.customer).isEqualTo(customer)
            assertThat(value.payment).isEqualTo(payment)
            assertThat(value.order).isEqualTo(order)
        }
    }

}
