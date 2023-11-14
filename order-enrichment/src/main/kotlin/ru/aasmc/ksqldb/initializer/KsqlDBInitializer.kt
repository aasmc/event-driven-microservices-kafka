package ru.aasmc.ksqldb.initializer

import io.confluent.ksql.api.client.Client
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component
import ru.aasmc.eventdriven.common.props.TopicsProps
import ru.aasmc.ksqldb.config.props.KsqlDBProps
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger(KsqlDBInitializer::class.java)

@Component
class KsqlDBInitializer(
    private val client: Client,
    topicProps: TopicsProps,
    ksqlDBProps: KsqlDBProps,
) : ApplicationListener<ContextRefreshedEvent> {

    private val CREATE_ORDERS_STREAM = """
        CREATE STREAM IF NOT EXISTS ${ksqlDBProps.ordersStream} (
            ID STRING KEY, 
            CUSTOMERID BIGINT, 
            STATE STRING, 
            PRODUCT STRING, 
            QUANTITY INT, 
            PRICE DOUBLE 
        ) 
        WITH (
            KAFKA_TOPIC='${topicProps.ordersTopic}',
            VALUE_FORMAT='AVRO';
        );
    """

    private val CREATE_CUSTOMERS_TABLE = """
          CREATE SOURCE TABLE IF NOT EXISTS ${ksqlDBProps.customersTable} (
              CUSTOMERID BIGINT PRIMARY KEY,
              FIRSTNAME STRING,
              LASTNAME STRING,
              EMAIL STRING,
              ADDRESS STRING,
              LEVEL STRING DEFAULT 'bronze'
          )
          WITH (
              KAFKA_TOPIC='${topicProps.customersTopic}',
              VALUE_FORMAT='AVRO',
          );
    """

    private val CREATE_ORDERS_ENRICHED_STREAM = """
        CREATE STREAM IF NOT EXISTS ${ksqlDBProps.ordersEnrichedStream}
            AS SELECT ${ksqlDBProps.customersTable}.CUSTOMERID AS customerId,
                      ${ksqlDBProps.customersTable}.FIRSTNAME,
                      ${ksqlDBProps.customersTable}.LASTNAME,
                      ${ksqlDBProps.customersTable}.LEVEL,
                      ${ksqlDBProps.ordersStream}.PRODUCT,
                      ${ksqlDBProps.ordersStream}.QUANTITY,
                      ${ksqlDBProps.ordersStream}.PRICE
            FROM ${ksqlDBProps.ordersStream}
            LEFT JOIN ${ksqlDBProps.customersTable}
                 ON ${ksqlDBProps.ordersStream}.CUSTOMERID = ${ksqlDBProps.customersTable}.CUSTOMERID;
       """

    private val CREATE_FRAUD_ORDER_TABLE = """
        CREATE TABLE IF NOT EXISTS ${ksqlDBProps.fraudOrderTable} 
        WITH (KEY_FORMAT='json') 
        AS SELECT CUSTOMERID,
                  LASTNAME,
                  FIRSTNAME,
                  COUNT(*) AS COUNTS
           FROM ${ksqlDBProps.ordersEnrichedStream} 
           WINDOW TUMBLING (SIZE 30 SECONDS)
           GROUP BY CUSTOMERID, LASTNAME, FIRSTNAME
           HAVING COUNT(*)>2;
    """

    override fun onApplicationEvent(event: ContextRefreshedEvent) {
        try {
            val ordersResult = client.executeStatement(CREATE_ORDERS_STREAM).get()
            log.info(
                "Result of creating orders stream: {}", ordersResult.queryId()
                    .orElse("Failed to create orders stream.")
            )
            Thread.sleep(1000)
            val customersResult = client.executeStatement(CREATE_CUSTOMERS_TABLE).get()
            log.info(
                "Result of creating customers table: {}", customersResult.queryId()
                    .orElse("Failed to create customers table.")
            )
            Thread.sleep(1000)
            val enrichedResult = client.executeStatement(CREATE_ORDERS_ENRICHED_STREAM).get()
            log.info(
                "Result of creating orders enriched stream: {}", enrichedResult.queryId()
                    .orElse("Failed to create orders_enriched_stream")
            )
            Thread.sleep(1000)
            val fraudResult = client.executeStatement(CREATE_FRAUD_ORDER_TABLE).get()
            log.info(
                "Result of creating fraud order table: {}", fraudResult.queryId()
                    .orElse("Failed to create fraud order table.")
            )
        } catch (e: Exception) {
            log.error("Exception while initializing ksqlDB. Message: {}", e.message)
        }
    }


}