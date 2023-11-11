package ru.aasmc.orders.utils

class Paths(
    val base: String
) {

    companion object {
        const val ORDERS_PATH = "/v1/orders/"
    }
    constructor(host: String, port: Int): this("http://$host:$port")

    fun urlGet(id: Int): String {
        return "$base$ORDERS_PATH$id"
    }

    fun urlGet(id: String): String {
        return "$base$ORDERS_PATH$id"
    }

    fun urlGetValidated(id: Int): String {
        return "$base$ORDERS_PATH$id/validated"
    }

    fun urlGetValidated(id: String): String {
        return "$base$ORDERS_PATH$id/validated"
    }

    fun urlPost(): String {
        return "$base$ORDERS_PATH"
    }
}