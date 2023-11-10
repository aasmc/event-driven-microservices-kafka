package ru.aasmc.validationaggregator

import org.springframework.boot.fromApplication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.with

@TestConfiguration(proxyBeanMethods = false)
class TestValidationAggregatorServiceApplication

fun main(args: Array<String>) {
    fromApplication<ValidationAggregatorServiceApplication>().with(TestValidationAggregatorServiceApplication::class)
        .run(*args)
}
