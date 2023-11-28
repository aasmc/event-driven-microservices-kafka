package ru.aasmc.fraud.service

import ru.aasmc.avro.eventdriven.Order
import ru.aasmc.avro.eventdriven.OrderState.CREATED
import ru.aasmc.avro.eventdriven.OrderValidation
import ru.aasmc.avro.eventdriven.OrderValidationResult.FAIL
import ru.aasmc.avro.eventdriven.OrderValidationResult.PASS
import ru.aasmc.avro.eventdriven.OrderValidationType.FRAUD_CHECK
import ru.aasmc.avro.eventdriven.Product.JUMPERS
import ru.aasmc.avro.eventdriven.Product.UNDERPANTS


val orders = listOf(
    Order("0", 0L, CREATED, UNDERPANTS, 3, 5.0),
    Order("1", 0L, CREATED, JUMPERS, 1, 75.0),
    Order("2", 1L, CREATED, JUMPERS, 1, 75.0),
    Order("3", 1L, CREATED, JUMPERS, 1, 75.0),
    Order("4", 1L, CREATED, JUMPERS, 50, 75.0), //Should fail as over limit
    Order("5", 2L, CREATED, UNDERPANTS, 10, 100.0), //First should pass
    Order("6", 3L, CREATED, UNDERPANTS, 10, 100.0), //Second should fail as rolling total by customer is over limit
    Order("7", 4L, CREATED, UNDERPANTS, 1, 5.0),  //Third should fail as rolling total by customer is still over limit
)

val expected = listOf(
    OrderValidation("0", FRAUD_CHECK, PASS),
    OrderValidation("1", FRAUD_CHECK, PASS),
    OrderValidation("2", FRAUD_CHECK, PASS),
    OrderValidation("3", FRAUD_CHECK, PASS),
    OrderValidation("4", FRAUD_CHECK, FAIL),
    OrderValidation("5", FRAUD_CHECK, PASS),
    OrderValidation("6", FRAUD_CHECK, FAIL),
    OrderValidation("7", FRAUD_CHECK, FAIL),
)