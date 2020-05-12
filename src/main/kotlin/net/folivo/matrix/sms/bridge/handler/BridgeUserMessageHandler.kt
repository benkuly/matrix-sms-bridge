package net.folivo.matrix.sms.bridge.handler

import net.folivo.matrix.bot.handler.MatrixMessageContentHandler
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.core.model.events.m.room.message.MessageEvent.MessageEventContent
import reactor.core.publisher.Mono

class BridgeUserMessageHandler : MatrixMessageContentHandler {
    override fun handleMessage(content: MessageEventContent, context: MessageContext): Mono<Void> {
        TODO("Not yet implemented")
    }
}