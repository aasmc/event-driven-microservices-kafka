package ru.aasmc.orders.dto

import ru.aasmc.avro.eventdriven.OrderState
import ru.aasmc.avro.eventdriven.Product

data class OrderDto(
    val id: String,
    val customerId: Long,
    val state: OrderState,
    val product: Product,
    val quantity: Int,
    val price: Double
)

