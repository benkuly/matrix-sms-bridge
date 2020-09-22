package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SendSmsService(
        private val smsBotProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties,
        private val smsProvider: SmsProvider
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun sendSms(
            room: AppserviceRoom,
            body: String,
            sender: String,
            context: MessageContext,
            isTextMessage: Boolean
    ) {

        room.members.entries
                .filter { it.key.userId != sender }
                .map { Triple(it.key, it.value, it.key.userId.removePrefix("@sms_").substringBefore(":")) }
                .filter { it.third.matches(Regex("[0-9]{6,15}")) }
                .map { (member, memberOfProps, receiver) ->
                    if (isTextMessage) {
                        LOG.debug("send SMS from ${room.roomId} to +$receiver")
                        try {
                            insertBodyAndSend(
                                    sender = sender,
                                    receiver = receiver,
                                    body = body,
                                    mappingToken = memberOfProps.mappingToken,
                                    needsToken = member.rooms.size > 1
                            )
                        } catch (error: Throwable) {
                            LOG.error(
                                    "Could not send sms from room ${room.roomId} and $sender. " +
                                    "This should be fixed.", error
                            ) // TODO it should send sms later
                            context.answer(
                                    NoticeMessageEventContent(smsBridgeProperties.templates.sendSmsError),
                                    asUserId = member.userId
                            )
                        }
                    } else {
                        LOG.debug("cannot send SMS from ${room.roomId} to +$receiver because of incompatible message type")
                        context.answer(
                                NoticeMessageEventContent(smsBridgeProperties.templates.sendSmsIncompatibleMessage),
                                asUserId = member.userId
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