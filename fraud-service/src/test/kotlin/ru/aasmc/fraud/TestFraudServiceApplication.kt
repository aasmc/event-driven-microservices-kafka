package ru.aasmc.fraud

import org.springframework.boot.fromApplication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.with

@TestConfiguration(proxyBeanMethods = false)
class TestFraudServiceApplication

fun main(args: Array<String>) {
    fromApplication<FraudServiceApplication>().with(TestFraudServiceApplication::class).run(*args)
}
