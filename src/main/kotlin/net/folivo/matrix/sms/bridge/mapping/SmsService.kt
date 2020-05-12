package net.folivo.matrix.sms.bridge.mapping

import reactor.core.publisher.Mono

interface SmsService {
    fun sendSms(receiver: String, body: String): Mono<Void>
}