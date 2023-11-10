package ru.aasmc.orders.config

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StoreQueryParameters
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.orders.config.props.KafkaProps
import ru.aasmc.orders.config.props.OrdersProps
import java.util.Properties

@Configuration
class KafkaConfig(
    private val orderProps: OrdersProps,
    private val kafkaProps: KafkaProps
) {

    @Bean
    fun ordersTopic(): NewTopic =
        TopicBuilder
            .name(orderProps.topic)
            .partitions(orderProps.partitions)
            .replicas(orderProps.replication)
            .build()

    @Bean
    fun ordersKStream(): KafkaStreams {
        val builder = StreamsBuilder()
        builder.table(
            orderProps.topic,
            Consumed.with(Serdes.String(), SpecificAvroSerde<Order>()),
            Materialized.`as`(orderProps.storeName)
        )
        val props = streamsConfig()
        return KafkaStreams(builder.build(), props)
    }

    @Bean
    fun ordersStore(): ReadOnlyKeyValueStore<String, Order> =
        ordersKStream().store(
            StoreQueryParameters.fromNameAndType(
                orderProps.storeName,
                QueryableStoreTypes.keyValueStore()
            )
        )

    @Bean
    fun streamsConfig(): Properties {
        return Properties().apply {
            put(StreamsConfig.APPLICATION_ID_CONFIG, kafkaProps.appId)
            put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProps.bootstrapServers)
            put(StreamsConfig.STATE_DIR_CONFIG, kafkaProps.stateDir)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProps.autoOffsetReset)
            put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, kafkaProps.processingGuarantee)
            put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, kafkaProps.commitInterval)
            put(StreamsConfig.consumerPrefix(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG), kafkaProps.sessionTimeout)
            put("schema.registry.url", kafkaProps.schemaRegistryUrl)
        }
    }
}