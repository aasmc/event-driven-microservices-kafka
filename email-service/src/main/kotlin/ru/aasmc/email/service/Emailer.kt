package ru.aasmc.email.service

import ru.aasmc.email.dto.EmailTuple

interface Emailer {

    fun sendEmail(details: EmailTuple)

}