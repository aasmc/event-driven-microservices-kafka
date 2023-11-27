package ru.aasmc.email.service

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import ru.aasmc.avro.eventdriven.Customer
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.Payment
import ru.aasmc.eventdriven.common.props.KafkaProps
import ru.aasmc.eventdriven.common.schemas.Schemas

@TestConfiguration
class TestConfig(
    private val schemas: Schemas,
    private val kafkaProps: KafkaProps
) {

    fun commonProducerConfig(suffix: String): MutableMap<String, Any> {
        return hashMapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProps.bootstrapServers,
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaProps.schemaRegistryUrl,
            ProducerConfig.RETRIES_CONFIG to 1,
            ProducerConfig.CLIENT_ID_CONFIG to kafkaProps.appId + suffix,
            ProducerConfig.ACKS_CONFIG to kafkaProps.acks,
        )
    }

    fun paymentProducerProps(): Map<String, Any> {
        val props = commonProducerConfig("-payment")
        val paymentSerializer = SpecificAvroSerializer<Payment>()
        val serializerProps = hashMapOf<String, Any>(
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaProps.schemaRegistryUrl
        )
        paymentSerializer.configure(serializerProps, false)
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = paymentSerializer.javaClass.name
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name
        return props
    }

    @Bean(name = ["paymentProducerFactory"])
    fun paymentProducerFactory(): ProducerFactory<String, Payment> {
        return DefaultKafkaProducerFactory(paymentProducerProps())
    }

    @Bean(name = ["paymentProducer"])
    fun paymentProducer(): KafkaTemplate<String, Payment> {
        return KafkaTemplate(paymentProducerFactory())
    }

    fun orderProducerProps(): Map<String, Any> {
        val props = commonProducerConfig("-order")
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = schemas.ORDERS.valueSerde.serializer().javaClass.name
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = schemas.ORDERS.keySerde.serializer().javaClass.name
        return props
    }

    @Bean(name = ["orderProducerFactory"])
    fun orderProducerFactory(): ProducerFactory<String, Order> {
        return DefaultKafkaProducerFactory(orderProducerProps())
    }

    @Bean(name = ["orderProducer"])
    fun orderProducer(): KafkaTemplate<String, Order> {
        return KafkaTemplate(orderProducerFactory())
    }

    fun customerProducerProps(): Map<String, Any> {
        val props = commonProducerConfig("-customer")
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = schemas.CUSTOMERS.valueSerde.serializer().javaClass.name
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = schemas.CUSTOMERS.keySerde.serializer().javaClass.name
        return props
    }

    @Bean(name = ["customerProducerFactory"])
    fun customerProducerFactory(): ProducerFactory<Long, Customer> {
        return DefaultKafkaProducerFactory(customerProducerProps())
    }

    @Bean(name = ["customerProducer"])
    fun customerProducer(): KafkaTemplate<Long, Customer> {
        return KafkaTemplate(customerProducerFactory())
    }
}