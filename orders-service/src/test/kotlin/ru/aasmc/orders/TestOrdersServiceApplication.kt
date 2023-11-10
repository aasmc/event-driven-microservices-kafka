package ru.aasmc.orders

import org.springframework.boot.fromApplication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.with

@TestConfiguration(proxyBeanMethods = false)
class TestOrdersServiceApplication

fun main(args: Array<String>) {
    fromApplication<OrdersServiceApplication>().with(TestOrdersServiceApplication::class).run(*args)
}
