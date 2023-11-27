package ru.aasmc.orders.controller

import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.OrderState
import ru.aasmc.avro.eventdriven.Product
import ru.aasmc.orders.config.props.OrdersProps
import ru.aasmc.orders.dto.OrderDto

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integtest")
@Testcontainers
@AutoConfigureWebTestClient(timeout = "30000")
class OrdersControllerTest {

    @Autowired
    private lateinit var client: WebTestClient

    @Autowired
    private lateinit var producer: KafkaTemplate<String, Order>

    @Autowired
    private lateinit var ordersProps: OrdersProps

    companion object {
        @Container
        @JvmStatic
        private val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.2"))

        const val BASE_URL = "/v1/orders"

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("kafkaprops.bootstrapServers") { kafkaContainer.bootstrapServers }
        }
    }

    @Test
    fun submitOrder_whenSuccess_returns_pathOfOrder() {
        val dto = getSimpleOrderDto()
        client.post()
            .uri(BASE_URL)
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(dto)
            .exchange()
            .expectBody(String::class.java).value { path ->
                assertThat(path).isEqualTo("/v1/orders/1")
            }
    }

    @Test
    fun getOrder_whenNoOrderDuringTimeoutPeriod_returnsError() {
        client.get()
            .uri("$BASE_URL/100?timeout=1000")
            .exchange()
            .expectStatus()
            .is5xxServerError
            .expectBody(String::class.java)
            .value { errorMsg ->
                assertThat(errorMsg).isEqualTo("Failed to get order by id= 100 due to timeout.")
            }
    }

    @Test
    fun getOrder_whenHasOrderAndWithinTimeoutPeriod_returnsOrder() {
        val order = getSimpleOrder()
        producer.send(ProducerRecord(ordersProps.topic, order.id, order)).get()

        client.get()
            .uri("$BASE_URL/${order.id}")
            .exchange()
            .expectStatus().isOk
            .expectBody(OrderDto::class.java)
            .value { dto ->
                assertThat(dto.id).isEqualTo(order.id)
                assertThat(dto.customerId).isEqualTo(order.customerId)
                assertThat(dto.state).isEqualTo(order.state)
                assertThat(dto.product).isEqualTo(order.product)
                assertThat(dto.quantity).isEqualTo(order.quantity)
                assertThat(dto.price).isEqualTo(order.price)
            }
    }

    @Test
    fun getValidatedOrder_whenOrderNotValidatedInitially_thenGetsValidatedWithinTimoutPeriod_returnsOrder() {
        val order = getSimpleOrder()
        producer.send(ProducerRecord(ordersProps.topic, order.id, order)).get()

        val validatedOrder = Order.newBuilder(order).setState(OrderState.VALIDATED).build()

        val t = Thread {
            Thread.sleep(1000)
            producer.send(ProducerRecord(ordersProps.topic, order.id, validatedOrder)).get()
        }

        t.start()

        client.get()
            .uri("$BASE_URL/${order.id}/validated?timeout=3000")
            .exchange()
            .expectStatus().isOk
            .expectBody(OrderDto::class.java)
            .value { dto ->
                assertThat(dto.id).isEqualTo(validatedOrder.id)
                assertThat(dto.customerId).isEqualTo(validatedOrder.customerId)
                assertThat(dto.state).isEqualTo(validatedOrder.state)
                assertThat(dto.product).isEqualTo(validatedOrder.product)
                assertThat(dto.quantity).isEqualTo(validatedOrder.quantity)
                assertThat(dto.price).isEqualTo(validatedOrder.price)
            }

        t.join()
    }

    @Test
    fun getValidatedOrder_whenOrderNotValidatedInitially_thenGetsValidatedNotWithinTimoutPeriod_returnsError() {
        val order = getSimpleOrder("1000")

        producer.send(ProducerRecord(ordersProps.topic, order.id, order)).get()

        val validatedOrder = Order.newBuilder(order).setState(OrderState.VALIDATED).build()

        val t = Thread {
            Thread.sleep(3000)
            producer.send(ProducerRecord(ordersProps.topic, order.id, validatedOrder)).get()
        }

        t.start()

        client.get()
            .uri("$BASE_URL/${order.id}/validated?timeout=10")
            .exchange()
            .expectStatus().is5xxServerError
        t.join()
    }


    private fun getSimpleOrder(id: String = "1"): Order = Order.newBuilder()
        .setId(id)
        .setCustomerId(1L)
        .setState(OrderState.CREATED)
        .setProduct(Product.JUMPERS)
        .setQuantity(1)
        .setPrice(1.0)
        .build()

    private fun getSimpleOrderDto(): OrderDto = OrderDto(
        id = "1",
        customerId = 1L,
        state = OrderState.CREATED,
        product = Product.JUMPERS,
        quantity = 1,
        price = 1.0
    )

    @Test
    fun contextLoads() {
    }

}