package ru.aasmc.fraud.config.props

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "fraudprops")
class FraudProps @ConstructorBinding constructor(
    val storeName: String
)