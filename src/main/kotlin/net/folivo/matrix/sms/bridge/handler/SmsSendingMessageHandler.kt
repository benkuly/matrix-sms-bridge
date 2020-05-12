package net.folivo.matrix.sms.bridge.handler

import net.folivo.matrix.bot.handler.MatrixMessageContentHandler
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.core.model.events.m.room.message.MessageEvent.MessageEventContent
import net.folivo.matrix.sms.bridge.mapping.SendSmsService
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

class SmsSendingMessageHandler(
        private val sendSmsService: SendSmsService
) : MatrixMessageContentHandler {

    private val logger = LoggerFactory.getLogger(SmsSendingMessageHandler::class.java)

    override fun handleMessage(content: MessageEventContent, context: MessageContext): Mono<Void> {
        logger.debug("handle message in room ${context.roomId}")
        return sendSmsService.sendSms(
                roomId = context.roomId,
                body = content.body,
                sender = context.originalEvent.sender
        )
    }
}