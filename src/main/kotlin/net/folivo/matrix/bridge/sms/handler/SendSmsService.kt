package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class SendSmsService(
        private val smsBridgeProperties: SmsBridgeProperties,
        private val smsProvider: SmsProvider,
        private val matrixClient: MatrixClient
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    fun sendSms(room: AppserviceRoom, body: String, sender: String): Mono<Void> {
        return Flux.fromIterable(room.members.entries)
                .filter { it.key.userId != sender }
                .flatMap { (member, memberOfProps) ->
                    val receiver = member.userId.removePrefix("@sms_").substringBefore(":")
                    if (receiver.matches(Regex("[0-9]{6,15}"))) {
                        LOG.debug("send SMS from ${room.roomId} to +$receiver")
                        val templateBody = smsBridgeProperties.templates.outgoingMessage
                                .replace("{sender}", sender)
                                .replace("{body}", body)
                                .replace("{token}", "#${memberOfProps.mappingToken}")
                        smsProvider.sendSms(receiver = "+$receiver", body = templateBody)
                    } else {
                        LOG.warn(
                                "Could not send SMS because the sender ${member.userId} didn't contain a valid telephone number." +
                                "Usually this should never happen because the Homeserver uses the same regex."
                        )
                        Mono.empty()
                    }.onErrorResume {
                        LOG.error(
                                "Could not send sms from room ${room.roomId} and $sender with body '$body'. " +
                                "This should be handled, e.g. by queuing messages.", it
                        )
                        matrixClient.roomsApi.sendRoomEvent(
                                roomId = room.roomId,
                                eventContent = NoticeMessageEventContent(
                                        smsBridgeProperties.templates.sendSmsError
                                ),
                                asUserId = member.userId
                        ).then()
                    }

                }
                .then()
    }
}