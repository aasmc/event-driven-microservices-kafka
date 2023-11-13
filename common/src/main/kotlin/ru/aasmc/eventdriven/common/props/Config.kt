package ru.aasmc.eventdriven.common.props

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
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

}