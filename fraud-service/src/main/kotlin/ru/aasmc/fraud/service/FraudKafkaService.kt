package ru.aasmc.fraud.service

import org.apache.kafka.streams.StreamsBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.aasmc.eventdriven.common.props.KafkaProps

private val log = LoggerFactory.getLogger(FraudKafkaService::class.java)

private const val FRAUD_LIMIT = 2000

@Service
class FraudKafkaService(
    private val kafkaProps: KafkaProps
) {

    @Autowired
    fun processStreams(builder: StreamsBuilder) {

    }

}