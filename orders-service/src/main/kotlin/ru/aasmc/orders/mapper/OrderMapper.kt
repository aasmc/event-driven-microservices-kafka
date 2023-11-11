package ru.aasmc.orders.mapper

import org.springframework.stereotype.Component
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.orders.dto.OrderDto

@Component
class OrderMapper {

    fun toDomain(dto: OrderDto): Order =
        Order(
            dto.id,
            dto.customerId,
            dto.state,
            dto.product,
            dto.quantity,
            dto.price
        )

    fun toDto(domain: Order): OrderDto =
        OrderDto(
            id = domain.id,
            customerId = domain.customerId,
            state = domain.state,
            product = domain.product,
            quantity = domain.quantity,
            price = domain.price
        )
}