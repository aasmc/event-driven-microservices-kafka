package ru.aasmc.inventory.service

import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.kstream.Transformer
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.processor.api.Processor
import org.apache.kafka.streams.state.KeyValueStore
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.OrderValidation
import ru.aasmc.avro.eventdriven.OrderValidationResult.FAIL
import ru.aasmc.avro.eventdriven.OrderValidationResult.PASS
import ru.aasmc.avro.eventdriven.OrderValidationType.INVENTORY_CHECK
import ru.aasmc.avro.eventdriven.Product
import ru.aasmc.inventory.config.props.InventoryProps

class InventoryValidator(
    private val inventoryProps: InventoryProps
) : Transformer<Product, KeyValue<Order, Int>, KeyValue<String, OrderValidation>> {

    private lateinit var store: KeyValueStore<Product, Long>
    private val reservedStocksStore: KeyValueStore<Product, Long>
        get() = store

    override fun init(context: ProcessorContext?) {
        store = context?.getStateStore(inventoryProps.reservedStockStoreName)
            ?: throw RuntimeException("State store ${inventoryProps.reservedStockStoreName} cannot be initialized!")
    }

    override fun transform(
        productId: Product,
        orderAndStock: KeyValue<Order, Int>
    ): KeyValue<String, OrderValidation> {
        //Process each order/inventory pair one at a time
        val order = orderAndStock.key
        val warehouseStockCount = orderAndStock.value

        //Look up locally 'reserved' stock from our state store
        var reserved = reservedStocksStore.get(order.product)
        if (reserved == null) {
            reserved = 0L
        }
        //If there is enough stock available (considering both warehouse inventory
        // and reserved stock) validate the order
        val validated = if (warehouseStockCount - reserved - order.quantity >= 0) {
            reservedStocksStore.put(order.product, reserved + order.quantity)
            // validate the order
            OrderValidation(order.id, INVENTORY_CHECK, PASS)
        } else {
            OrderValidation(order.id, INVENTORY_CHECK, FAIL)
        }
        return KeyValue.pair(validated.orderId, validated)
    }

    override fun close() {
    }


}