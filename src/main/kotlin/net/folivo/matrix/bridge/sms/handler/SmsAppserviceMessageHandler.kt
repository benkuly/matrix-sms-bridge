package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.handler.MatrixMessageContentHandler
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.room.AppserviceRoomRepository
import net.folivo.matrix.core.model.events.m.room.message.MessageEvent.MessageEventContent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SmsAppserviceMessageHandler(
        private val sendSmsService: SendSmsService,
        private val smsBotMessageHandler: SmsBotMessageHandler,
        private val roomRepository: AppserviceRoomRepository,
        private val botProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties
) : MatrixMessageContentHandler {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override fun handleMessage(content: MessageEventContent, context: MessageContext): Mono<Void> {
        val roomId = context.roomId
        LOG.debug("handle message in room $roomId")
        return if (context.roomId == smsBridgeProperties.defaultRoomId) {
            LOG.debug("ignored message to default room")
            Mono.empty()
        } else {
            roomRepository.findById(roomId)
                    // FIXME switch if empty?
                    .flatMap { room ->
                        if (room.members.let {
                                    it.size == 1
                                    && it.keys.first().userId == "@${botProperties.username}:${botProperties.serverName}"
                                }) {
                            smsBotMessageHandler.handleMessageToSmsBot(
                                    room = room,
                                    body = content.body,
                                    sender = context.originalEvent.sender
                            )
                        } else {
                            sendSmsService.sendSms(
                                    room = room,
                                    body = content.body,
                                    sender = context.originalEvent.sender
                            )
                        }
                    }
        }
    }
}