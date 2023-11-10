package ru.aasmc.orders

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class OrdersServiceApplication

fun main(args: Array<String>) {
    runApplication<OrdersServiceApplication>(*args)
}
