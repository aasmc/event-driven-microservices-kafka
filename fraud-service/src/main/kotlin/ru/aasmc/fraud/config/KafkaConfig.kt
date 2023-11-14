package ru.aasmc.fraud.config

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafkaStreams
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration
import org.springframework.kafka.config.KafkaStreamsConfiguration
import ru.aasmc.eventdriven.common.props.KafkaProps
import ru.aasmc.eventdriven.common.props.TopicsProps
import ru.aasmc.eventdriven.common.util.ServiceUtils

@EnableKafkaStreams
@Configuration
class KafkaConfig(
    private val kafkaProps: KafkaProps,
    private val serviceUtils: ServiceUtils,
    private val topicProps: TopicsProps
) {

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
            StreamsConfig.consumerPrefix(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG) to kafkaProps.sessionTimeout,
            // disable caching to ensure a complete aggregate changelog.
            // This is a little trick we need to apply
            // as caching in Kafka Streams will conflate subsequent updates for the same key.
            // Disabling caching ensures
            // we get a complete "changelog" from the aggregate(...)
            // (i.e. every input event will have a corresponding output event.
            // https://cwiki.apache.org/confluence/pages/viewpage.action?pageId=186878390
            StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG to "0"
        )
        return KafkaStreamsConfiguration(props)
    }

}