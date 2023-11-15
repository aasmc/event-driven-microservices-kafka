package ru.aasmc.populator.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
class WebClientConfig {

    @Bean
    fun webclientBuilder(): WebClient.Builder {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(5))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(5, TimeUnit.SECONDS))
                conn.addHandlerLast(WriteTimeoutHandler(5, TimeUnit.SECONDS))
            }
        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
    }

}