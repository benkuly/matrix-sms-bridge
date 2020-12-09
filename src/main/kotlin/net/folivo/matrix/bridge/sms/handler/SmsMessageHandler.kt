package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.event.MatrixMessageHandler
import net.folivo.matrix.bot.event.MessageContext
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.core.model.events.m.room.message.MessageEvent.MessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SmsMessageHandler(
    private val messageToSmsHandler: MessageToSmsHandler,
    private val messageToBotHandler: MessageToBotHandler,
    private val membershipService: MatrixMembershipService,
    private val botProperties: MatrixBotProperties,
    private val smsBridgeProperties: SmsBridgeProperties
) : MatrixMessageHandler {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override suspend fun handleMessage(content: MessageEventContent, context: MessageContext) {
        val roomId = context.roomId
        val senderId = context.originalEvent.sender
        LOG.debug("handle message in room $roomId from sender $senderId")

        if (context.roomId == smsBridgeProperties.defaultRoomId || content is NoticeMessageEventContent) {
            LOG.debug("ignored notice message or message to default room")
            return
        } else {
            val didHandleMessage =
                membershipService.doesRoomContainsMembers(roomId, setOf(botProperties.botUserId))
                        && messageToBotHandler.handleMessage(
                    roomId = roomId,
                    body = content.body,
                    senderId = senderId,
                    context = context
                )

            if (didHandleMessage) {
                LOG.debug("ignored message because it was for bot or only a notice message")
                return
            } else {
                messageToSmsHandler.handleMessage(
                    roomId = roomId,
                    body = content.body,
                    senderId = senderId,
                    context = context,
                    isTextMessage = content is TextMessageEventContent
                )
            }
        }
    }
}