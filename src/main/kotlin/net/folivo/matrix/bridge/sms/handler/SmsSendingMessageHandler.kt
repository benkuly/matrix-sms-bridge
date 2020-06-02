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
import java.util.*

@Component
class SmsSendingMessageHandler(
        private val sendSmsService: SendSmsService,
        private val smsBotHandler: SmsBotHandler,
        private val roomRepository: AppserviceRoomRepository,
        private val botProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties
) : MatrixMessageContentHandler {

    private val logger = LoggerFactory.getLogger(SmsSendingMessageHandler::class.java)

    override fun handleMessage(content: MessageEventContent, context: MessageContext): Mono<Void> {
        val roomId = context.roomId
        logger.debug("handle message in room $roomId")
        return if (context.roomId == smsBridgeProperties.defaultRoomId) {
            logger.debug("ignored message to default room")
            Mono.empty()
        } else {
            roomRepository.findById(roomId)
                    .map { Optional.of(it) }
                    .defaultIfEmpty(Optional.empty())
                    .flatMap { room ->
                        if (room.isPresent && room.get().members.let {
                                    it.size == 1
                                    && it.keys.first().userId == "@${botProperties.username}:${botProperties.serverName}"
                                }) {
                            smsBotHandler.handleMessageToSmsBot(
                                    roomId = roomId,
                                    body = content.body,
                                    sender = context.originalEvent.sender
                            )
                        } else {
                            sendSmsService.sendSms(
                                    roomId = roomId,
                                    body = content.body,
                                    sender = context.originalEvent.sender
                            )
                        }
                    }
        }
    }
}