package ru.aasmc.ksqldb

data class FraudDto(
    val customerId: Long,
    val lastName: String,
    val firstName: String,
    val counts: Long
)
