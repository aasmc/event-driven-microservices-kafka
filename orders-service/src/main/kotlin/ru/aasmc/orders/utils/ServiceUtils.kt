package ru.aasmc.orders.utils

import org.springframework.stereotype.Component
import ru.aasmc.orders.config.props.ApplicationProps

@Component
class ServiceUtils(
    private val applicationProps: ApplicationProps
) {
    fun getServerAddress(): String {
        return "${getHost()}:${applicationProps.port}"
    }

    fun getHost(): String {
        return applicationProps.host
    }

    fun getPort(): Int {
        return applicationProps.port
    }

}