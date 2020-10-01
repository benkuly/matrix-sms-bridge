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

@Component
class SmsAppserviceMessageHandler(
        private val messageToSmsHandler: MessageToSmsHandler,
        private val messageToBotHandler: MessageToBotHandler,
        private val roomService: SmsMatrixAppserviceRoomService,
        private val botProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties
) : MatrixMessageContentHandler {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override suspend fun handleMessage(content: MessageEventContent, context: MessageContext) {
        val roomId = context.roomId
        val sender = context.originalEvent.sender
        LOG.debug("handle message in room $roomId from sender $sender")

        if (context.roomId == smsBridgeProperties.defaultRoomId) {
            LOG.debug("ignored message to default room")
            return
        } else {
            val room = roomService.getOrCreateRoom(roomId)
            val wasForBot = if (content is TextMessageEventContent && room.members.map { it.member.userId }
                            .contains("@${botProperties.username}:${botProperties.serverName}")) {
                messageToBotHandler.handleMessage(
                        room = room,
                        body = content.body,
                        sender = sender,
                        context = context
                )
            } else {
                LOG.debug("room didn't contain bot user or event was no text message")
                false
            }
            if (wasForBot || content is NoticeMessageEventContent) {
                LOG.debug("ignored message because it was for bot or only a notice message")
                return
            } else {
                messageToSmsHandler.handleMessage(
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