package ru.aasmc.orderdetails.config

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.eventdriven.common.props.KafkaProps
import ru.aasmc.eventdriven.common.schemas.Schemas
import java.util.UUID

@EnableKafka
@Configuration
class KafkaConfig(
    private val kafkaProps: KafkaProps,
    private val schemas: Schemas
) {

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, Order>
    ): ConcurrentKafkaListenerContainerFactory<String, Order> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, Order>()
        factory.consumerFactory = consumerFactory
        return factory
    }

    @Bean
    fun consumerFactory(): ConsumerFactory<String, Order> {
        return DefaultKafkaConsumerFactory(consumerProps())
    }

    private fun consumerProps(): Map<String, Any> {
        val props = hashMapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProps.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to kafkaProps.appId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaProps.autoOffsetReset,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to !kafkaProps.enableExactlyOnce,
            ConsumerConfig.CLIENT_ID_CONFIG to kafkaProps.appId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to schemas.ORDERS.keySerde.deserializer().javaClass.name,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to schemas.ORDERS.valueSerde.deserializer().javaClass.name,
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaProps.schemaRegistryUrl
        )
        return props
    }

    @Bean
    fun producerFactory(): ProducerFactory<String, Order> {
        return DefaultKafkaProducerFactory(senderProps())
    }

    private fun senderProps(): Map<String, Any> {
        val props = hashMapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProps.bootstrapServers,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to kafkaProps.enableIdempotence,
            ProducerConfig.RETRIES_CONFIG to Int.MAX_VALUE.toString(),
            ProducerConfig.ACKS_CONFIG to kafkaProps.acks,
            ProducerConfig.CLIENT_ID_CONFIG to kafkaProps.appId,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to schemas.ORDERS.valueSerde.serializer().javaClass.name,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to schemas.ORDERS.keySerde.serializer().javaClass.name,
            AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to false,
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaProps.schemaRegistryUrl

        )
        if (kafkaProps.enableExactlyOnce) {
            props[ProducerConfig.TRANSACTIONAL_ID_CONFIG] = kafkaProps.appId + UUID.randomUUID().leastSignificantBits.toString()
        }
        return props
    }

    @Bean
    fun kafkaTemplate(
        producerFactory: ProducerFactory<String, Order>
    ): KafkaTemplate<String, Order> {
        return KafkaTemplate(producerFactory)
    }

}