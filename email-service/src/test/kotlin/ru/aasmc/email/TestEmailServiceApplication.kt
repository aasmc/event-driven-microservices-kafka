package ru.aasmc.email

import org.springframework.boot.fromApplication
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.with

@TestConfiguration(proxyBeanMethods = false)
class TestEmailServiceApplication

fun main(args: Array<String>) {
    fromApplication<EmailServiceApplication>().with(TestEmailServiceApplication::class).run(*args)
}
