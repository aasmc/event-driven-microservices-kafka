package ru.aasmc.orderdetails

import org.springframework.boot.fromApplication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.with

@TestConfiguration(proxyBeanMethods = false)
class TestOrderDetailsServiceApplication

fun main(args: Array<String>) {
    fromApplication<OrderDetailsServiceApplication>().with(TestOrderDetailsServiceApplication::class).run(*args)
}
