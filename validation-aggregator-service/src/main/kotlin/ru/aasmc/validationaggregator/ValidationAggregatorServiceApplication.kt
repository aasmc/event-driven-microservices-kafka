package ru.aasmc.validationaggregator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ValidationAggregatorServiceApplication

fun main(args: Array<String>) {
    runApplication<ValidationAggregatorServiceApplication>(*args)
}
