package ru.aasmc.inventory.service

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.*
import org.apache.kafka.streams.state.KeyValueStore
import org.apache.kafka.streams.state.StoreBuilder
import org.apache.kafka.streams.state.Stores
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.OrderState
import ru.aasmc.avro.eventdriven.OrderValidation
import ru.aasmc.avro.eventdriven.Product
import ru.aasmc.eventdriven.common.props.TopicsProps
import ru.aasmc.eventdriven.common.schemas.Schemas
import ru.aasmc.inventory.config.props.InventoryProps

/**
 * This service validates incoming orders to ensure there is sufficient stock to
 * fulfill them. This validation process considers both the inventory in the warehouse
 * as well as a set "reserved" items which is maintained by this service. Reserved
 * items are those that are in the warehouse, but have been allocated to a pending
 * order.
 * <p>
 * Currently there is nothing implemented that decrements the reserved items. This
 * would happen, inside this service, in response to an order being shipped.
 */

private val log = LoggerFactory.getLogger(InventoryKafkaService::class.java)

@Service
class InventoryKafkaService(
    private val schemas: Schemas,
    private val inventoryProps: InventoryProps,
    private val topicProps: TopicsProps
) {

    @Autowired
    fun processStreams(builder: StreamsBuilder) {


        // Latch onto instances of the orders and inventory topics
        val orders: KStream<String, Order> = builder
            .stream(
                schemas.ORDERS.name,
                Consumed.with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde)
            )
            .peek { key, value ->
                log.info("Orders streams record. Key: {}, Order: {}", key, value)
            }

        val warehouseInventory: KTable<Product, Int> = builder
            .table(
                schemas.WAREHOUSE_INVENTORY.name,
                Consumed.with(
                    schemas.WAREHOUSE_INVENTORY.keySerde,
                    schemas.WAREHOUSE_INVENTORY.valueSerde
                )
            )
        // Create a store to reserve inventory whilst the order is processed.
        // This will be prepopulated from Kafka before the service starts processing
        val reservedStock: StoreBuilder<KeyValueStore<Product, Long>> = Stores
            .keyValueStoreBuilder(
                Stores.persistentKeyValueStore(inventoryProps.reservedStockStoreName),
                schemas.WAREHOUSE_INVENTORY.keySerde, Serdes.Long()
            )
            .withLoggingEnabled(hashMapOf())
        builder.addStateStore(reservedStock)

        val orderValidationsKeySerde = Serdes.String()
        val orderValidationsValueSerde = SpecificAvroSerde<OrderValidation>()


        //First change orders stream to be keyed by Product (so we can join with warehouse inventory)
        orders.selectKey { id, order -> order.product }
            .peek { key, value ->
                log.info("Orders stream record after SELECT KEY. New key: {}. \nNew value: {}", key, value)
            }
            // Limit to newly created orders
            .filter { id, order -> OrderState.CREATED == order.state }
            //Join Orders to Inventory so we can compare each order to its corresponding stock value
            .join(
                warehouseInventory,
                ::KeyValue,
                Joined.with(
                    schemas.WAREHOUSE_INVENTORY.keySerde,
                    schemas.ORDERS.valueSerde,
                    Serdes.Integer()
                )
            )
            //Validate the order based on how much stock we have both in the warehouse
            // and locally 'reserved' stock
            .transform(
                TransformerSupplier {
                    InventoryValidator(inventoryProps)
                },
                inventoryProps.reservedStockStoreName
            )
            .peek { key, value ->
                log.info(
                    "Pushing the result of validation Order record to topic: {} with key: {}. \nResult: {}",
                    schemas.ORDER_VALIDATIONS.name, key, value
                )
            }
            //Push the result into the Order Validations topic
            .to(
                schemas.ORDER_VALIDATIONS.name,
                Produced.with(
                    orderValidationsKeySerde.apply { configureSerde(this, true) },
                    orderValidationsValueSerde.apply { configureSerde(this, false) }
                )
            )
    }

    private fun configureSerde(serde: Serde<*>, isKey: Boolean) {
        val serdesConfig = hashMapOf<String, Any>(
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to topicProps.schemaRegistryUrl,
            AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to false,
            AbstractKafkaSchemaSerDeConfig.USE_LATEST_VERSION to true,
        )
        schemas.configureSerde(serde, serdesConfig, isKey)
    }
}
