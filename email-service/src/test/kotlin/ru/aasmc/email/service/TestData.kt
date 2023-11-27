package ru.aasmc.email.service

import ru.aasmc.avro.eventdriven.Customer
import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.OrderState
import ru.aasmc.avro.eventdriven.Payment
import ru.aasmc.avro.eventdriven.Product

const val ORDER_ID = "1"
const val CUSTOMER_ID = 15L
const val PAYMENT_ID = "Payment:1234"


val order = Order.newBuilder()
    .setId(ORDER_ID)
    .setCustomerId(CUSTOMER_ID)
    .setState(OrderState.CREATED)
    .setProduct(Product.UNDERPANTS)
    .setQuantity(3)
    .setPrice(5.00)
    .build()

val customer = Customer.newBuilder()
    .setId(CUSTOMER_ID)
    .setFirstName("Franz")
    .setLastName("Kafka")
    .setEmail("frans@thedarkside.net")
    .setAddress("oppression street, prague, cze")
    .setLevel("gold")
    .build()

val payment = Payment.newBuilder()
    .setId(PAYMENT_ID)
    .setOrderId(ORDER_ID)
    .setCcy("CZK")
    .setAmount(1000.0)
    .build()

