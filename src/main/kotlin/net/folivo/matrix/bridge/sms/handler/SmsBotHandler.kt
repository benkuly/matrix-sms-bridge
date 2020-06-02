package net.folivo.matrix.bridge.sms.handler

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SmsBotHandler {
    private val logger = LoggerFactory.getLogger(SendSmsService::class.java)

    fun handleMessageToSmsBot(roomId: String, body: String, sender: String): Mono<Void> {
        logger.info("currently SmsBotHandler is not implemented. from: $sender room: $roomId message: $body")
        return Mono.empty()
    }
}