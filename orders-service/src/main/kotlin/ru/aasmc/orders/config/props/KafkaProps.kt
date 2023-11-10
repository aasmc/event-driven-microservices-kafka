package ru.aasmc.orders.config.props

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "kafkaprops")
class KafkaProps @ConstructorBinding constructor(
    val bootstrapServers: String,
    val schemaRegistryUrl: String,
    val autoOffsetReset: String,
    val processingGuarantee: String,
    val commitInterval: Int,
    val sessionTimeout: Int,
    val stateDir: String,
    val appId: String
)