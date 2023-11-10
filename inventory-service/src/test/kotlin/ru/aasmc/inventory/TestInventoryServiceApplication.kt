package ru.aasmc.inventory

import org.springframework.boot.fromApplication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.with

@TestConfiguration(proxyBeanMethods = false)
class TestInventoryServiceApplication

fun main(args: Array<String>) {
    fromApplication<InventoryServiceApplication>().with(TestInventoryServiceApplication::class).run(*args)
}
