package ru.aasmc.validationaggregator.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.aasmc.eventdriven.common.props.KafkaProps
import ru.aasmc.eventdriven.common.schemas.Schemas

private val log = LoggerFactory.getLogger(ValidationAggregatorService::class.java)

@Service
class ValidationAggregatorService(
    private val schemas: Schemas,
    private val kafkaProps: KafkaProps
) {
}