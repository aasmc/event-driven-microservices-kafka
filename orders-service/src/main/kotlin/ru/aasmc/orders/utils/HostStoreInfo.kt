package ru.aasmc.orders.utils

data class HostStoreInfo(
    val host: String = "",
    val port: Int = 0,
    val storeNames: MutableSet<String> = hashSetOf()
)