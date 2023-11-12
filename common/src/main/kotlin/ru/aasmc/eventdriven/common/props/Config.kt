package ru.aasmc.eventdriven.common.props

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsConfig
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.annotation.EnableKafkaStreams
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration
import org.springframework.kafka.config.KafkaStreamsConfiguration
import org.springframework.kafka.config.TopicBuilder
import ru.aasmc.eventdriven.common.util.ServiceUtils

@Configuration
@EnableKafka
@EnableKafkaStreams
@ConfigurationPropertiesScan
class Config(
    private val topicProps: TopicsProps,
    private val kafkaProps: KafkaProps,
    private val serviceUtils: ServiceUtils
) {

    @Bean
    fun ordersTopic(): NewTopic =
        TopicBuilder
            .name(topicProps.ordersTopic)
            .partitions(topicProps.partitions)
            .replicas(topicProps.replication)
            .build()

    @Bean
    fun orderValidationTopic(): NewTopic =
        TopicBuilder
            .name(topicProps.orderValidationTopic)
            .partitions(topicProps.partitions)
            .replicas(topicProps.replication)
            .build()

    @Bean
    fun ordersEnrichedTopic(): NewTopic =
        TopicBuilder
            .name(topicProps.ordersEnrichedTopic)
            .partitions(topicProps.partitions)
            .replicas(topicProps.replication)
            .build()

    @Bean
    fun inventoryTopic(): NewTopic =
        TopicBuilder
            .name(topicProps.inventoryTopic)
            .partitions(topicProps.partitions)
            .replicas(topicProps.replication)
            .build()


    @Bean
    fun paymentsTopic(): NewTopic =
        TopicBuilder
            .name(topicProps.paymentsTopic)
            .partitions(topicProps.partitions)
            .replicas(topicProps.replication)
            .build()

    @Bean
    fun customersTopic(): NewTopic =
        TopicBuilder
            .name(topicProps.customersTopic)
            .partitions(topicProps.partitions)
            .replicas(topicProps.replication)
            .build()

    @Bean
    fun platinumTopic(): NewTopic =
        TopicBuilder
            .name(topicProps.platinumTopic)
            .partitions(topicProps.partitions)
            .replicas(topicProps.replication)
            .build()

    @Bean
    fun goldTopic(): NewTopic =
        TopicBuilder
            .name(topicProps.goldTopic)
            .partitions(topicProps.partitions)
            .replicas(topicProps.replication)
            .build()

    @Bean
    fun silverTopic(): NewTopic =
        TopicBuilder
            .name(topicProps.silverTopic)
            .partitions(topicProps.partitions)
            .replicas(topicProps.replication)
            .build()

    @Bean
    fun bronzeTopic(): NewTopic =
        TopicBuilder
            .name(topicProps.bronzeTopic)
            .partitions(topicProps.partitions)
            .replicas(topicProps.replication)
            .build()

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
            StreamsConfig.consumerPrefix(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG) to kafkaProps.sessionTimeout
        )
        return KafkaStreamsConfiguration(props)
    }
}