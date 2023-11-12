package ru.aasmc.eventdriven.common.props

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "topicprops")
class TopicsProps @ConstructorBinding constructor(
    val ordersTopic: String,
    val ordersEnrichedTopic: String,
    val inventoryTopic: String,
    val orderValidationTopic: String,
    val paymentsTopic: String,
    val customersTopic: String,
    val platinumTopic: String,
    val goldTopic: String,
    val silverTopic: String,
    val bronzeTopic: String,
    val schemaRegistryUrl: String,
    val partitions: Int,
    val replication: Int
)