package ru.aasmc.populator.config

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Serdes
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import ru.aasmc.avro.eventdriven.Payment
import ru.aasmc.avro.eventdriven.Product
import ru.aasmc.eventdriven.common.props.KafkaProps
import ru.aasmc.eventdriven.common.schemas.ProductTypeSerde

@Configuration
class KafkaConfig(
    private val kafkaProps: KafkaProps
) {

    fun commonProducerProps(): MutableMap<String, Any> {
        return hashMapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProps.bootstrapServers,
            ProducerConfig.ACKS_CONFIG to kafkaProps.acks,
            ProducerConfig.RETRIES_CONFIG to 1,
            ProducerConfig.CLIENT_ID_CONFIG to kafkaProps.appId + "-inventory"
        )
    }

    fun inventoryProducerProps(): Map<String, Any> {
        val props = commonProducerProps()
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = ProductTypeSerde().serializer().javaClass.name
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = Serdes.Integer().javaClass.name
        return props
    }

    @Bean(name = ["inventoryProducerFactory"])
    fun inventoryProducerFactory(): ProducerFactory<Product, Int> {
        return DefaultKafkaProducerFactory(inventoryProducerProps())
    }

    @Bean(name = ["inventoryProducer"])
    fun inventoryProducer(): KafkaTemplate<Product, Int> {
        return KafkaTemplate(inventoryProducerFactory())
    }

    fun paymentProducerProps(): Map<String, Any> {
        val props = commonProducerProps()
        val paymentSerializer = SpecificAvroSerializer<Payment>()
        val serializerProps = hashMapOf<String, Any>(
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaProps.schemaRegistryUrl
        )
        paymentSerializer.configure(serializerProps, false)
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = paymentSerializer.javaClass.name
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = Serdes.String().serializer().javaClass.name
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
}