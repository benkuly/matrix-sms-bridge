package net.folivo.matrix.bridge.sms.mapping

import net.folivo.matrix.bot.appservice.room.AppserviceRoomRepository
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

// FIXME test
@Service
class SendSmsService(
        private val appserviceRoomRepository: AppserviceRoomRepository,
        private val smsRoomService: SmsRoomService,
        private val smsBridgeProperties: SmsBridgeProperties,
        private val smsProvider: SmsProvider,
        private val matrixClient: MatrixClient
) {

    private val logger = LoggerFactory.getLogger(SendSmsService::class.java)

    fun sendSms(roomId: String, body: String, sender: String): Mono<Void> {
        return appserviceRoomRepository.findById(roomId)
                .flatMapMany { Flux.fromIterable(it.members) }
                .filter { it.userId != sender }
                .flatMap { member ->
                    smsRoomService.getBridgedSmsRoom(roomId, member.userId).map {
                        smsBridgeProperties.templates.outgoingMessage
                                .replace("{sender}", sender)
                                .replace("{body}", body)
                                .replace("{token}", "#" + it.mappingToken.toString())
                    }.flatMap { body ->
                        val receiver = member.userId.trimStart(*"@sms_".toCharArray()).substringBefore(":")
                        if (receiver.matches(Regex("[0-9]{6,15}"))) {
                            logger.debug("send SMS from $roomId to +$receiver")
                            smsProvider.sendSms(receiver = "+$receiver", body = body)
                        } else {
                            logger.warn(
                                    "Could not send SMS because the sender ${member.userId} didn't contain a valid telephone number." +
                                    "Usually this should never happen because the Homeserver uses the same regex."
                            )
                            Mono.empty()
                        }
                                .onErrorResume {
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
                }
                .then()
    }
}