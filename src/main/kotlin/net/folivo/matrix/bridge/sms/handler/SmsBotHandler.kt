package net.folivo.matrix.bridge.sms.handler

import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SmsBotHandler {
    fun handleMessageToSmsBot(roomId: String, body: String, sender: String): Mono<Void> {
        return Mono.empty()
    }
}