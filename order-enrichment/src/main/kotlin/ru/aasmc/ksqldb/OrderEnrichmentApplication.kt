package ru.aasmc.ksqldb

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan("ru.aasmc")
@ConfigurationPropertiesScan
class OrderEnrichmentApplication

fun main(args: Array<String>) {
    runApplication<OrderEnrichmentApplication>(*args)
}