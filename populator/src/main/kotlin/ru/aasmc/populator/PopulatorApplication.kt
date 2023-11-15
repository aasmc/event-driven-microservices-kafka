package ru.aasmc.populator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@ComponentScan("ru.aasmc")
@SpringBootApplication
@ConfigurationPropertiesScan
class PopulatorApplication

fun main(args: Array<String>) {
    runApplication<PopulatorApplication>(*args)
}