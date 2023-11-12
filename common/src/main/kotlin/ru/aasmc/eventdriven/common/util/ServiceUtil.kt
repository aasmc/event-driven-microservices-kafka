package ru.aasmc.eventdriven.common.util

import org.springframework.stereotype.Component
import ru.aasmc.eventdriven.common.props.ApplicationProps

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