package ru.aasmc.populator.service

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.streams.KeyValue
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import ru.aasmc.avro.eventdriven.OrderState
import ru.aasmc.avro.eventdriven.Payment
import ru.aasmc.avro.eventdriven.Product
import ru.aasmc.eventdriven.common.props.TopicsProps
import ru.aasmc.populator.config.props.PopulatorProps
import ru.aasmc.populator.dto.OrderDto
import java.util.*
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger(PopulatorService::class.java)

@Service
class PopulatorService(
    private val webClientBuilder: WebClient.Builder,
    private val inventoryProducer: KafkaTemplate<Product, Int>,
    private val paymentProducer: KafkaTemplate<String, Payment>,
    private val populatorProps: PopulatorProps,
    private val topicProps: TopicsProps
) {

    fun populate() {
        sendInventories()
        sendPayments()
    }

    private fun sendInventories() {
        val quantityUnderpants = 20
        val quantityJumpers = 10
        val inventory = listOf(
            KeyValue(Product.UNDERPANTS, quantityUnderpants),
            KeyValue(Product.JUMPERS, quantityJumpers)
        )
        log.info("Sending inventory to Kafka.")
        sendInventory(inventory)
    }

    private fun sendInventory(
        inventory: List<KeyValue<Product, Int>>
    ) {
        for (kv in inventory) {
            val record = ProducerRecord(topicProps.inventoryTopic, kv.key, kv.value)
            inventoryProducer.send(record)
                .whenComplete { res, ex ->
                    if (ex != null) {
                        log.error("Failed to send inventory {}", kv)
                    } else {
                        log.info("Successfully sent inventory {}", kv)
                    }
                }
        }
    }

    private fun sendPayments() {
        val numCustomers = 6
        val randomGenerator = Random()
        val productTypeList = listOf<Product>(
            Product.JUMPERS, Product.UNDERPANTS, Product.STOCKINGS
        )
        var startingOrderId = 1
        // send one order every 1 second
        while (!Thread.currentThread().isInterrupted) {
            val randomCustomerId = randomGenerator.nextInt(numCustomers).toLong()
            val randomProduct = productTypeList[randomGenerator.nextInt(productTypeList.size)]

            val inputOrder = OrderDto(
                startingOrderId.toString(),
                randomCustomerId,
                OrderState.CREATED,
                randomProduct,
                1,
                1.0
            )
            log.info("Posting order: {} to ${populatorProps.orderServiceUrl}", inputOrder)
            try {
                val client = webClientBuilder.build()
                client.post()
                    .uri(populatorProps.orderServiceUrl)
                    .bodyValue(inputOrder)
                    .retrieve()
                    .bodyToMono<String>()
                    .subscribe {
                        log.info("Created order path: $it")
                    }
                val payment = Payment("Payment:1234", startingOrderId.toString(), "CZK", 1000.0)
                sendPayment(payment.id, payment)
                ++startingOrderId
            } catch (e: Exception) {
                log.error("Error communication with Orders Service, retrying shortly.")
            }
            TimeUnit.SECONDS.sleep(1)
        }
    }

    private fun sendPayment(
        id: String,
        payment: Payment
    ) {
        val record = ProducerRecord(topicProps.paymentsTopic, id, payment)
        paymentProducer.send(record)
            .whenComplete { res, ex ->
                if (ex != null) {
                    log.error("Failed to send payment {} to kafka topic.", payment)
                } else {
                    log.info("Successfully sent payment {} to kafka topic.", payment)
                }
            }
    }

}