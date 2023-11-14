package ru.aasmc.ksqldb.config.props

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "ksqldbprops")
class KsqlDBProps @ConstructorBinding constructor(
    val host: String,
    val port: Int,
    val ordersStream: String,
    val customersTable: String,
    val ordersEnrichedStream: String,
    val fraudOrderTable: String
)