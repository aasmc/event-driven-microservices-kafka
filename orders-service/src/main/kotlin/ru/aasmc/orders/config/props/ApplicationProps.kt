package ru.aasmc.orders.config.props

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "application")
class ApplicationProps @ConstructorBinding constructor(
    val host: String,
    val port: Int
)