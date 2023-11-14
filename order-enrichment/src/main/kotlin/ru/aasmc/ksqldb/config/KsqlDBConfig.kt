package ru.aasmc.ksqldb.config

import io.confluent.ksql.api.client.Client
import io.confluent.ksql.api.client.ClientOptions
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.aasmc.ksqldb.config.props.KsqlDBProps

@Configuration
class KsqlDBConfig(
    private val props: KsqlDBProps
) {

    @Bean
    fun ksqlDBClient(): Client {
        val options = ClientOptions.create()
            .setHost(props.host)
            .setPort(props.port)
        return Client.create(options)
    }

}