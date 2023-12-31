package ru.aasmc.eventdriven.common.props

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.TopicBuilder
import ru.aasmc.eventdriven.common.util.ServiceUtils

@Configuration
@EnableKafka
@ConfigurationPropertiesScan
class Config(
    private val topicProps: TopicsProps
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