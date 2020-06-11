package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.handler.MatrixMessageContentHandler
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.core.model.events.m.room.message.MessageEvent.MessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SmsAppserviceMessageHandler(
        private val sendSmsService: SendSmsService,
        private val smsBotMessageHandler: SmsBotMessageHandler,
        private val roomService: SmsMatrixAppserviceRoomService,
        private val botProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties
) : MatrixMessageContentHandler {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override fun handleMessage(content: MessageEventContent, context: MessageContext): Mono<Void> {
        val roomId = context.roomId
        val sender = context.originalEvent.sender
        LOG.debug("handle message in room $roomId from sender $sender")
        return if (context.roomId == smsBridgeProperties.defaultRoomId) {
            LOG.debug("ignored message to default room")
            Mono.empty()
        } else {
            roomService.getRoomOrCreateAndJoin(roomId, sender)
                    .flatMap { room ->
                        if (content is TextMessageEventContent && room.members.keys.find { it.userId == "@${botProperties.username}:${botProperties.serverName}" } != null) {
                            smsBotMessageHandler.handleMessageToSmsBot(
                                    room = room,
                                    body = content.body,
                                    sender = sender,
                                    context = context
                            ).map { Pair(it, room) }
                        } else {
                            LOG.debug("room didn't contain bot user or event was no text message")
                            Mono.just(Pair(false, room))
                        }
                    }.flatMap { (wasForBot, room) ->
                        if (wasForBot || content is NoticeMessageEventContent) {
                            Mono.empty()
                        } else {
                            sendSmsService.sendSms(
                                    room = room,
                                    body = content.body,
                                    sender = sender,
                                    context = context,
                                    isTextMessage = content is TextMessageEventContent
                            )
                        }
                    }
        }
    }
}