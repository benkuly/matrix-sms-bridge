package net.folivo.matrix.bridge.sms.provider

import reactor.core.publisher.Mono

interface SmsService {
    fun sendSms(receiver: String, body: String): Mono<Void>
}