package ru.aasmc.email

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@ComponentScan("ru.aasmc")
@SpringBootApplication
@ConfigurationPropertiesScan
class EmailServiceApplication

fun main(args: Array<String>) {
    runApplication<EmailServiceApplication>(*args)
}
