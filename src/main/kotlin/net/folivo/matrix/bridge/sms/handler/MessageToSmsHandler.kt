package net.folivo.matrix.bridge.sms.handler

import kotlinx.coroutines.flow.*
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.event.MessageContext
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bot.user.MatrixUserService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.mapping.MatrixSmsMappingService
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.core.model.MatrixId.RoomId
import net.folivo.matrix.core.model.MatrixId.UserId
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

    private val templates = smsBridgeProperties.templates

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
                .collect { (receiverNumber, receiverId) ->
                    if (isTextMessage) {
                        LOG.debug("send SMS from $roomId to $receiverNumber")
                        val needsToken = !smsBridgeProperties.allowMappingWithoutToken
                                         || !roomService.getOrCreateRoom(roomId).isManaged
                                         && roomService.getRoomsByMembers(setOf(receiverId)).take(2).count() > 1
                        val mappingToken = if (needsToken)
                            mappingService.getOrCreateMapping(receiverId, roomId).mappingToken else null

                        try {
                            insertBodyAndSend(
                                    sender = senderId,
                                    receiverNumber = receiverNumber,
                                    body = body,
                                    mappingToken = mappingToken
                            )
                        } catch (error: Throwable) {
                            LOG.error(
                                    "Could not send sms from room $roomId and $senderId. " +
                                    "This should be fixed.", error
                            ) // TODO it should send sms later
                            context.answer(
                                    templates.sendSmsError.replace("{error}", error.message ?: "unknown"),
                                    asUserId = receiverId
                            )
                        }
                    } else {
                        LOG.debug("cannot send SMS from $roomId to $receiverNumber because of incompatible message type")
                        context.answer(templates.sendSmsIncompatibleMessage, asUserId = receiverId)
                    }
                }
    }

    private suspend fun insertBodyAndSend(
            sender: UserId,
            receiverNumber: String,
            body: String,
            mappingToken: Int?
    ) {
        val messageTemplate =
                if (sender == botProperties.botUserId)
                    templates.outgoingMessageFromBot
                else templates.outgoingMessage
        val completeTemplate =
                if (mappingToken == null) messageTemplate
                else messageTemplate + templates.outgoingMessageToken.replace("{token}", "#$mappingToken")

        val templateBody = completeTemplate
                .replace("{sender}", sender.full)
                .replace("{body}", body)

        smsProvider.sendSms(receiver = receiverNumber, body = templateBody)
    }
}