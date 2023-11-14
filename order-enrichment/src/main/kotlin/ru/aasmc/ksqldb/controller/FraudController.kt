package ru.aasmc.ksqldb.controller

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.aasmc.ksqldb.FraudDto
import ru.aasmc.ksqldb.service.FraudService

private val log = LoggerFactory.getLogger(FraudController::class.java)

@RestController
@RequestMapping("/fraud")
class FraudController(
    private val fraudService: FraudService
) {

    @GetMapping
    fun getFraudulentOrders(
        @RequestParam(name = "limit", defaultValue = "10") limit: Int
    ): List<FraudDto> {
        log.info("Received request to GET $limit number of fraudulent orders.")
        return fraudService.getFraudulentOrders(limit)
    }

}