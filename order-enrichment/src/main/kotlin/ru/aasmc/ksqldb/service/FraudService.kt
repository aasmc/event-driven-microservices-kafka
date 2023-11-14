package ru.aasmc.ksqldb.service

import ru.aasmc.ksqldb.FraudDto

interface FraudService {

    fun getFraudulentOrders(limit: Int): List<FraudDto>

}