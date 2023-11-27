package ru.aasmc.email.config

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafkaStreams
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration
import org.springframework.kafka.config.KafkaStreamsConfiguration
import ru.aasmc.avro.eventdriven.Customer
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.OrderEnriched
import ru.aasmc.avro.eventdriven.Payment
import ru.aasmc.email.dto.EmailTuple
import ru.aasmc.email.service.Emailer
import ru.aasmc.eventdriven.common.props.KafkaProps
import ru.aasmc.eventdriven.common.props.TopicsProps
import ru.aasmc.eventdriven.common.schemas.Schemas
import ru.aasmc.eventdriven.common.util.ServiceUtils
import java.time.Duration

@EnableKafkaStreams
@Configuration
class KafkaConfig(
    private val kafkaProps: KafkaProps,
    private val serviceUtils: ServiceUtils,
    private val topicProps: TopicsProps,
    private val schemas: Schemas
) {

    @Bean
    fun ordersStream(builder: StreamsBuilder): KStream<String, Order> =
        builder.stream(
            schemas.ORDERS.name,
            Consumed.with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde)
        )

    @Bean
    fun paymentsStream(builder: StreamsBuilder): KStream<String, Payment> =
        builder.stream(
            schemas.PAYMENTS.name,
            Consumed.with(schemas.PAYMENTS.keySerde, schemas.PAYMENTS.valueSerde)
        )
            //Rekey payments to be by OrderId for the windowed join
            .selectKey { s, payment -> payment.orderId }

    @Bean
    fun customersTable(builder: StreamsBuilder): GlobalKTable<Long, Customer> =
        builder.globalTable(
            schemas.CUSTOMERS.name,
            Consumed.with(schemas.CUSTOMERS.keySerde, schemas.CUSTOMERS.valueSerde)
        )

    @Bean(name = ["ordersPaymentsCustomers"])
    fun ordersJoinPaymentsJoinCustomersStream(
        orders: KStream<String, Order>,
        payments: KStream<String, Payment>,
        customers: GlobalKTable<Long, Customer>,
        emailer: Emailer
    ): KStream<String, EmailTuple> {
        val serdes: StreamJoined<String, Order, Payment> = StreamJoined
            .with(schemas.ORDERS.keySerde, schemas.ORDERS.valueSerde, schemas.PAYMENTS.valueSerde)
        //Join the two streams and the table then send an email for each
        return orders.join(
            payments,
            ::EmailTuple,
            JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMinutes(1)),
            serdes
        )
            //Next join to the GKTable of Customers
            .join(
                customers,
                { _, value -> value.order.customerId },
                // note how, because we use a GKtable, we can join on any attribute of the Customer.
                EmailTuple::setCustomer
            )
            //Now for each tuple send an email.
            .peek { _, emailTuple ->
                emailer.sendEmail(emailTuple)
            }
    }

    @Bean(name = ["ordersCustomers"])
    fun ordersJoinCustomersStream(
        orders: KStream<String, Order>,
        customers: GlobalKTable<Long, Customer>
    ): KStream<String, OrderEnriched> {
        return orders.join(
            customers,
            { orderId, order -> order.customerId },
            { order, customer ->
                OrderEnriched(order.id, order.customerId, customer.level)
            }
        )
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
            StreamsConfig.consumerPrefix(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG) to kafkaProps.sessionTimeout
        )
        return KafkaStreamsConfiguration(props)
    }
}