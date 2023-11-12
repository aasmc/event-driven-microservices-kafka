package ru.aasmc.email.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.aasmc.email.dto.EmailTuple

private val log = LoggerFactory.getLogger(LoggingEmailer::class.java)

@Service
class LoggingEmailer : Emailer {

    override fun sendEmail(details: EmailTuple) {
        log.warn(
            "Sending email: \nCustomer:{}\nOrder:{}\nPayment:{}",
            details.customer,
            details.order,
            details.payment
        )
    }

}