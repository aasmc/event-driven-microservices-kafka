package ru.aasmc.eventdriven.common.schemas

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import jakarta.annotation.PostConstruct
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.Serializer
import org.springframework.stereotype.Component
import ru.aasmc.avro.eventdriven.*
import ru.aasmc.eventdriven.common.props.TopicsProps
import java.nio.charset.StandardCharsets

val ALL = hashMapOf<String, Topic<*, *>>()

class Topic<K, V>(
    val name: String,
    val keySerde: Serde<K>,
    val valueSerde: Serde<V>
) {

    init {
        ALL[name] = this
    }
    override fun toString(): String {
        return name
    }
}

class ProductSerializer: Serializer<Product> {
    override fun serialize(topic: String?, pt: Product): ByteArray {
        return pt.toString().toByteArray(StandardCharsets.UTF_8)
    }

    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {

    }

    override fun close() {

    }

}

class ProductDeserializer: Deserializer<Product> {
    override fun deserialize(topic: String?, data: ByteArray): Product {
        return Product.valueOf(String(data, StandardCharsets.UTF_8))
    }

    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {
    }

    override fun close() {
    }
}

open class ProductTypeSerde: Serde<Product> {
    override fun configure(configs: MutableMap<String, *>?, isKey: Boolean) {
    }

    override fun close() {

    }

    override fun serializer(): Serializer<Product> = ProductSerializer()

    override fun deserializer(): Deserializer<Product> = ProductDeserializer()

}

@Component
class Schemas(
    private val topicsProps: TopicsProps
) {

    val ORDERS = Topic<String, Order>(topicsProps.ordersTopic, Serdes.String(), SpecificAvroSerde())
    val ORDERS_ENRICHED = Topic<String, OrderEnriched>(topicsProps.ordersEnrichedTopic, Serdes.String(), SpecificAvroSerde())
    val PAYMENTS = Topic<String, Payment>(topicsProps.paymentsTopic, Serdes.String(), SpecificAvroSerde())
    val CUSTOMERS = Topic<Long, Customer>(topicsProps.customersTopic, Serdes.Long(), SpecificAvroSerde())
    val ORDER_VALIDATIONS = Topic<String, OrderValidation>(topicsProps.orderValidationTopic, Serdes.String(), SpecificAvroSerde())
    val WAREHOUSE_INVENTORY = Topic<Product, Int>(topicsProps.inventoryTopic, ProductTypeSerde(), Serdes.Integer())
    val ORDER_VALUE_SERDE = SpecificAvroSerde<OrderValue>()

    @PostConstruct
    fun configureTopics() {
        configureSerdes()
    }

    private fun configureSerdes() {
        val config = hashMapOf<String, Any>(
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to topicsProps.schemaRegistryUrl,
//            AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to false,
//            AbstractKafkaSchemaSerDeConfig.USE_LATEST_VERSION to true,
//            AbstractKafkaSchemaSerDeConfig.ID_COMPATIBILITY_STRICT to false
        )
        for (topic in ALL.values) {
            configureSerde(topic.keySerde, config, true)
            configureSerde(topic.valueSerde, config, false)
        }
        configureSerde(ORDER_VALUE_SERDE, config, false)
    }

    fun configureSerde(serde: Serde<*>, config: Map<String, Any>, isKey: Boolean) {
        if (serde is SpecificAvroSerde) {
            serde.configure(config, isKey)
        }
    }
}


