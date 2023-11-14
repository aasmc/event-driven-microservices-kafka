package ru.aasmc.orderdetails.service

import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Service
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.OrderState
import ru.aasmc.avro.eventdriven.OrderValidation
import ru.aasmc.avro.eventdriven.OrderValidationResult
import ru.aasmc.avro.eventdriven.OrderValidationType
import ru.aasmc.eventdriven.common.props.KafkaProps
import ru.aasmc.eventdriven.common.schemas.Schemas

private val log = LoggerFactory.getLogger(KafkaOrderDetailsService::class.java)

/**
 * Validates the details of each order.
 * - Is the quantity positive?
 * - Is there a customerId
 * - etc...
 * <p>
 * This service could be built with Kafka Streams but we've used a Producer/Consumer pair
 * including the integration with Kafka's Exactly Once feature (Transactions) to demonstrate
 * this other style of building event driven services. Care needs to be taken with this approach
 * as in the current release multi-node support is not provided for the transactional consumer
 * (but it is supported inside Kafka Streams)
 */
@Service
class KafkaOrderDetailsService(
    private val producer: KafkaTemplate<String, OrderValidation>,
    private val schemas: Schemas,
    private val kafkaProps: KafkaProps
) {

    @KafkaListener(
        id = "\${kafkaprops.appId}",
        topics = ["\${topicprops.ordersTopic}"],
        groupId = "\${kafkaprops.appId}",
        autoStartup = "true"
    )
    fun ordersListener(
        record: Order,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int
    ) {
        log.info("Handling order in listener: {}", record)
        if (kafkaProps.enableExactlyOnce) {
            sendInTransaction(record, topic, offset, partition)
        } else {
            sendWithoutTransaction(record)
        }
    }

    private fun sendWithoutTransaction(order: Order) {
        log.info("Sending order {} without transaction.", order)
        sendOrderValidationToKafka(order)
    }

    private fun sendInTransaction(order: Order, topic: String, offset: Long, partition: Int) {
        producer.executeInTransaction { kt ->
            val consumedOffsets = hashMapOf<TopicPartition, OffsetAndMetadata>()
            log.info("Sending order {} in transaction.", order)
            if (OrderState.CREATED == order.state) {
                sendOrderValidationToKafka(order)
                recordOffset(consumedOffsets, topic, offset, partition)
            }
            producer.sendOffsetsToTransaction(consumedOffsets, ConsumerGroupMetadata(kafkaProps.appId))
        }
    }

    private fun sendOrderValidationToKafka(order: Order) {
        val validationResult = if (isValid(order)) {
            OrderValidationResult.PASS
        } else {
            OrderValidationResult.FAIL
        }
        val producerRecord = result(order, validationResult)
        producer.send(producerRecord)
    }


    private fun recordOffset(
        consumedOffsets: MutableMap<TopicPartition, OffsetAndMetadata>,
        topic: String,
        offset: Long,
        partition: Int
    ) {
        val nextOffset = OffsetAndMetadata(offset + 1)
        consumedOffsets[TopicPartition(topic, partition)] = nextOffset
    }

    private fun result(
        order: Order,
        passOrFail: OrderValidationResult
    ): ProducerRecord<String, OrderValidation> {
        return ProducerRecord(
            schemas.ORDER_VALIDATIONS.name,
            order.id,
            OrderValidation(order.id, OrderValidationType.ORDER_DETAILS_CHECK, passOrFail)
        )
    }

    private fun isValid(order: Order): Boolean {
        return if (order.quantity < 0) {
            false
        } else if (order.price < 0) {
            false
        } else {
            order.product != null
        }
    }
}