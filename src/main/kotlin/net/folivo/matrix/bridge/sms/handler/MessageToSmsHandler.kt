package net.folivo.matrix.bridge.sms.handler

import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.event.MessageContext
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bot.user.MatrixUserService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.mapping.MatrixSmsMappingService
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.core.model.MatrixId.RoomId
import net.folivo.matrix.core.model.MatrixId.UserId
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MessageToSmsHandler(
        private val botProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties,
        private val smsProvider: SmsProvider,
        private val roomService: MatrixRoomService,
        private val userService: MatrixUserService,
        private val mappingService: MatrixSmsMappingService
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun handleMessage(
            roomId: RoomId,
            body: String,
            senderId: UserId,
            context: MessageContext,
            isTextMessage: Boolean
    ) {

        userService.getUsersByRoom(roomId)
                .filter { it.isManaged && it.id != senderId && it.id != botProperties.botUserId }
                .map { "+" + it.id.localpart.removePrefix("sms_") to it.id }
                .map { (receiverNumber, receiverId) ->
                    if (isTextMessage) {
                        LOG.debug("send SMS from $roomId to $receiverNumber")
                        val mappingToken = mappingService.getOrCreateMapping(receiverId, roomId).mappingToken
                        val roomIsManaged = roomService.getOrCreateRoom(roomId).isManaged
                        val needsToken = roomService.getRoomsByMembers(setOf(receiverId))//FIXME disable token in single mode when from managed room
                                                 .take(2)
                                                 .count() > 1 && !roomIsManaged // FIXME test
                        try {
                            insertBodyAndSend(
                                    sender = senderId,
                                    receiverNumber = receiverNumber,
                                    body = body,
                                    mappingToken = mappingToken,
                                    needsToken = needsToken
                            )
                        } catch (error: Throwable) {
                            LOG.error(
                                    "Could not send sms from room $roomId and $senderId. " +
                                    "This should be fixed.", error
                            ) // TODO it should send sms later
                            context.answer(
                                    NoticeMessageEventContent(smsBridgeProperties.templates.sendSmsError),
                                    asUserId = receiverId
                            )
                        }
                    } else {
                        LOG.debug("cannot send SMS from $roomId to $receiverNumber because of incompatible message type")
                        context.answer(
                                NoticeMessageEventContent(smsBridgeProperties.templates.sendSmsIncompatibleMessage),
                                asUserId = receiverId
                        )
                    }
                }
    }

    private suspend fun insertBodyAndSend(
            sender: UserId,
            receiverNumber: String,
            body: String,
            mappingToken: Int,
            needsToken: Boolean
    ) {
        val messageTemplate =
                if (sender == botProperties.botUserId)
                    smsBridgeProperties.templates.outgoingMessageFromBot
                else smsBridgeProperties.templates.outgoingMessage
        val completeTemplate =
                if (smsBridgeProperties.allowMappingWithoutToken && !needsToken) messageTemplate
                else messageTemplate + smsBridgeProperties.templates.outgoingMessageToken

        val templateBody = completeTemplate.replace("{sender}", sender.full)
                .replace("{body}", body)
                .replace("{token}", "#$mappingToken")

        smsProvider.sendSms(receiver = receiverNumber, body = templateBody)
    }
}