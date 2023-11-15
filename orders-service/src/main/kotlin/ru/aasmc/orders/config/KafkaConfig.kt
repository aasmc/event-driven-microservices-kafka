package ru.aasmc.orders.config

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafkaStreams
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration
import org.springframework.kafka.config.KafkaStreamsConfiguration
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.eventdriven.common.props.KafkaProps
import ru.aasmc.eventdriven.common.props.TopicsProps
import ru.aasmc.eventdriven.common.util.ServiceUtils
import ru.aasmc.orders.dto.OrderDto
import ru.aasmc.orders.utils.FilteredResponse
import java.util.concurrent.ConcurrentHashMap

@EnableKafkaStreams
@Configuration
class KafkaConfig(
    private val kafkaProps: KafkaProps,
    private val serviceUtils: ServiceUtils,
    private val topicProps: TopicsProps
) {

    @Bean
    fun outstandingRequests(): MutableMap<String, FilteredResponse<String, Order, OrderDto>> {
        return ConcurrentHashMap()
    }

    @Bean(name = [KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME])
    fun kStreamsConfig(): KafkaStreamsConfiguration {
        val props = hashMapOf<String, Any>(
            StreamsConfig.APPLICATION_ID_CONFIG to kafkaProps.appId,
            // must be specified to enable InteractiveQueries and checking metadata of Kafka Cluster
            StreamsConfig.APPLICATION_SERVER_CONFIG to serviceUtils.getServerAddress(),
            StreamsConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaProps.bootstrapServers,
            StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG to Serdes.String().javaClass.name,
            // instances MUST have different stateDir
            StreamsConfig.STATE_DIR_CONFIG to kafkaProps.stateDir,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaProps.autoOffsetReset,
            StreamsConfig.PROCESSING_GUARANTEE_CONFIG to kafkaProps.processingGuarantee,
            StreamsConfig.COMMIT_INTERVAL_MS_CONFIG to kafkaProps.commitInterval,
            AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to topicProps.schemaRegistryUrl,
            AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to false,
            StreamsConfig.consumerPrefix(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG) to kafkaProps.sessionTimeout
        )
        return KafkaStreamsConfiguration(props)
    }

}