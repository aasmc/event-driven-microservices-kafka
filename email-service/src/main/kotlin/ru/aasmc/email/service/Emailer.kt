package ru.aasmc.email.service

import ru.aasmc.email.dto.EmailTuple

fun interface Emailer {

    fun sendEmail(details: EmailTuple)

}