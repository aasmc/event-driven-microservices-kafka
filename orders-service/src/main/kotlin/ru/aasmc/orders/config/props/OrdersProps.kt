package ru.aasmc.orders.config.props

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "ordersprops")
class OrdersProps @ConstructorBinding constructor (
    val topic: String,
    val partitions: Int,
    val replication: Int,
    val storeName: String
)