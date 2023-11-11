package ru.aasmc.orders.utils

import org.springframework.web.context.request.async.DeferredResult

data class FilteredResponse<K, V, D>(
    val asyncResponse: DeferredResult<D>,
    val predicate: (K, V) -> Boolean
)