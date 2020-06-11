package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class SendSmsService(
        private val smsBotProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties,
        private val smsProvider: SmsProvider
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    fun sendSms(
            room: AppserviceRoom,
            body: String,
            sender: String,
            context: MessageContext,
            isTextMessage: Boolean
    ): Mono<Void> {
        return Flux.fromIterable(room.members.entries)
                .filter { it.key.userId != sender }
                .map { Triple(it.key, it.value, it.key.userId.removePrefix("@sms_").substringBefore(":")) }
                .filter { it.third.matches(Regex("[0-9]{6,15}")) }
                .flatMap { (member, memberOfProps, receiver) ->
                    if (isTextMessage) {
                        LOG.debug("send SMS from ${room.roomId} to +$receiver")
                        insertBodyAndSend(
                                sender = sender,
                                receiver = receiver,
                                body = body,
                                mappingToken = memberOfProps.mappingToken,
                                needsToken = member.rooms.size > 1
                        ).onErrorResume {
                            LOG.error(
                                    "Could not send sms from room ${room.roomId} and $sender with body '$body'. " +
                                    "This should be handled, e.g. by queuing messages.", it
                            )
                            context.answer(
                                    NoticeMessageEventContent(smsBridgeProperties.templates.sendSmsError),
                                    asUserId = member.userId
                            ).then()
                        }
                    } else {
                        LOG.debug("cannot SMS from ${room.roomId} to +$receiver because of incompatible message type")
                        context.answer(
                                NoticeMessageEventContent(smsBridgeProperties.templates.sendSmsIncompatibleMessage),
                                asUserId = member.userId
                        )
                    }
                }
                .then()
    }

    private fun insertBodyAndSend(
            sender: String,
            receiver: String,
            body: String,
            mappingToken: Int,
            needsToken: Boolean
    ): Mono<Void> {
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

        return smsProvider.sendSms(receiver = "+$receiver", body = templateBody)
    }
}