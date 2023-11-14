package ru.aasmc.inventory.config.props

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "inventoryprops")
class InventoryProps @ConstructorBinding constructor(
    val reservedStockStoreName: String
)