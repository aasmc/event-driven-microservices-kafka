package ru.aasmc.email.dto

import ru.aasmc.avro.eventdriven.Customer
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.Payment

data class EmailTuple(
    val order: Order,
    val payment: Payment
) {
    var customer: Customer? = null

    fun setCustomer(customer: Customer): EmailTuple {
        this.customer = customer
        return this
    }
}
