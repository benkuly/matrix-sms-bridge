package net.folivo.matrix.bridge.sms.handler

import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.membership.MembershipService
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.bridge.sms.user.SmsMatrixAppserviceUserService
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MessageToSmsHandler(
        private val smsBotProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties,
        private val smsProvider: SmsProvider,
        private val roomService: SmsMatrixAppserviceRoomService,
        private val userService: SmsMatrixAppserviceUserService,
        private val membershipService: MembershipService
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun handleMessage(
            roomId: String,
            body: String,
            senderId: String,
            context: MessageContext,
            isTextMessage: Boolean
    ) {

        userService.getUsersByRoomId(roomId)
                .filter { it.id != senderId && it.isManaged }
                .map { it.id.removePrefix("@sms_").substringBefore(":") to it.id }
                .filter { (receiverNumber, _) -> receiverNumber.matches(Regex("[0-9]{6,15}")) } // FIXME do we need this?
                .map { (receiverNumber, receiverId) ->
                    if (isTextMessage) {
                        LOG.debug("send SMS from $roomId to +$receiverNumber")
                        val mappingToken = membershipService.getOrCreateMembership(receiverId, roomId).mappingToken
                        val needsToken = roomService.getRooms(receiverId).take(2).count() > 1 // FIXME test
                        try {
                            insertBodyAndSend(
                                    sender = senderId,
                                    receiver = receiverNumber,
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
                        LOG.debug("cannot send SMS from $roomId to +$receiverNumber because of incompatible message type")
                        context.answer(
                                NoticeMessageEventContent(smsBridgeProperties.templates.sendSmsIncompatibleMessage),
                                asUserId = receiverId
                        )
                    }
                }
    }

    private suspend fun insertBodyAndSend(
            sender: String,
            receiver: String,
            body: String,
            mappingToken: Int,
            needsToken: Boolean
    ) {
        val messageTemplate =
                if (sender == "@${smsBotProperties.username}:${smsBotProperties.serverName}")
                    smsBridgeProperties.templates.outgoingMessageFromBot
                else smsBridgeProperties.templates.outgoingMessage
        val completeTemplate =
                if (smsBridgeProperties.allowMappingWithoutToken && !needsToken) messageTemplate
                else messageTemplate + smsBridgeProperties.templates.outgoingMessageToken

        val templateBody = completeTemplate.replace("{sender}", sender)
                .replace("{body}", body)
                .replace("{token}", "#$mappingToken")

        smsProvider.sendSms(receiver = "+$receiver", body = templateBody)
    }
}