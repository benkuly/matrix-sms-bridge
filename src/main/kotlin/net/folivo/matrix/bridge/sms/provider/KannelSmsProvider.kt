package net.folivo.matrix.bridge.sms.provider

import reactor.core.publisher.Mono

class KannelSmsProvider : SmsProvider {
    override fun sendSms(receiver: String, body: String): Mono<Void> {
        TODO("Not yet implemented")
    }
}