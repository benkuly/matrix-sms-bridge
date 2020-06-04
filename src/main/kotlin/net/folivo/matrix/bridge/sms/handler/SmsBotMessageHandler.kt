package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SmsBotMessageHandler {
    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    fun handleMessageToSmsBot(room: AppserviceRoom, body: String, sender: String): Mono<Void> {
        LOG.info("currently SmsBotHandler is not implemented. from: $sender room: ${room.roomId} message: $body")
        return Mono.empty()
    }
}