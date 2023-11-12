package ru.aasmc.orders.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.orders.dto.OrderDto
import ru.aasmc.orders.utils.FilteredResponse
import java.util.concurrent.ConcurrentHashMap

@Configuration
class KafkaConfig {

    @Bean
    fun outstandingRequests(): MutableMap<String, FilteredResponse<String, Order, OrderDto>> {
        return ConcurrentHashMap()
    }

}