package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.bridge.sms.room.AppserviceRoomRepository
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class SendSmsService(
        private val appserviceRoomRepository: AppserviceRoomRepository,
        private val smsBridgeProperties: SmsBridgeProperties,
        private val smsProvider: SmsProvider,
        private val matrixClient: MatrixClient
) {

    private val logger = LoggerFactory.getLogger(SendSmsService::class.java)

    fun sendSms(roomId: String, body: String, sender: String): Mono<Void> {
        return appserviceRoomRepository.findById(roomId)
                .flatMapMany { Flux.fromIterable(it.members.entries) }
                .filter { it.key.userId != sender }
                .flatMap { memberWithProps ->
                    val member = memberWithProps.key
                    val templateBody = smsBridgeProperties.templates.outgoingMessage
                            .replace("{sender}", sender)
                            .replace("{body}", body)
                            .replace("{token}", "#${memberWithProps.value.mappingToken}")
                    val receiver = member.userId.removePrefix("@sms_").substringBefore(":")
                    if (receiver.matches(Regex("[0-9]{6,15}"))) {
                        logger.debug("send SMS from $roomId to +$receiver")
                        smsProvider.sendSms(receiver = "+$receiver", body = templateBody)
                    } else {
                        logger.warn(
                                "Could not send SMS because the sender ${member.userId} didn't contain a valid telephone number." +
                                "Usually this should never happen because the Homeserver uses the same regex."
                        )
                        Mono.empty()
                    }.onErrorResume {
                        logger.error(
                                "Could not send sms from room $roomId and $sender with body '$body'. " +
                                "This should be handled, e.g. by queuing messages.", it
                        )
                        matrixClient.roomsApi.sendRoomEvent(
                                roomId = roomId,
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