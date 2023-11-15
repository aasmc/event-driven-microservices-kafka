package ru.aasmc.populator.config.props

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties(prefix = "populatorprops")
class PopulatorProps @ConstructorBinding constructor(
    val orderServiceUrl: String
)