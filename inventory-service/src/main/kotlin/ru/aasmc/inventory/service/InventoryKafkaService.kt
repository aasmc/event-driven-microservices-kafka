package ru.aasmc.inventory.service

import org.springframework.stereotype.Service
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
@Service
class InventoryKafkaService(
    private val schemas: Schemas,
    private val inventoryProps: InventoryProps
) {
}