package ru.aasmc.ksqldb.service

import io.confluent.ksql.api.client.Client
import io.confluent.ksql.api.client.Row
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.aasmc.ksqldb.FraudDto
import ru.aasmc.ksqldb.config.props.KsqlDBProps

private val log = LoggerFactory.getLogger(FraudServiceImpl::class.java)

@Service
class FraudServiceImpl(
    private val client: Client,
    private val ksqlDBProps: KsqlDBProps
) : FraudService {
    override fun getFraudulentOrders(limit: Int): List<FraudDto> {
        log.info("Executing getFraudulentOrders stream query.")
        val frauds = client.executeQuery("SELECT * FROM ${ksqlDBProps.fraudOrderTable} LIMIT $limit;").get()
        return frauds.map(::mapRowToResponse)
    }

    private fun mapRowToResponse(row: Row): FraudDto {
        log.info("Mapping ksqlDB Query row: {}", row)
        val cusId = row.getLong("CUSTOMERID")
        val lastName = row.getString("LASTNAME")
        val firstName = row.getString("FIRSTNAME")
        val count = row.getLong("COUNTS")
        return FraudDto(cusId, lastName, firstName, count)
    }
}