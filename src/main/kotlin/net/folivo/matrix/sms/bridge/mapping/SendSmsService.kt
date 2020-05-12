package net.folivo.matrix.sms.bridge.mapping

import net.folivo.matrix.bot.appservice.room.AppserviceRoomRepository
import net.folivo.matrix.sms.bridge.SmsBridgeProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class SendSmsService(
        private val appserviceRoomRepository: AppserviceRoomRepository,
        private val smsRoomService: SmsRoomService,
        private val smsBridgeProperties: SmsBridgeProperties,
        private val smsService: SmsService
) {

    private val logger = LoggerFactory.getLogger(SendSmsService::class.java)

    fun sendSms(roomId: String, body: String, sender: String): Mono<Void> {
        return appserviceRoomRepository.findById(roomId)
                .flatMapMany { Flux.fromIterable(it.members) }
                .flatMap { member ->
                    smsRoomService.getBridgedSmsRoom(roomId, member.userId).map {
                        smsBridgeProperties.templates.outgoingMessage
                                .replace("{username}", sender)
                                .replace("{body}", body)
                                .replace("{token}", "#" + it.mappingToken.toString())
                    }.flatMap { body ->
                        val receiver = Regex("\\+[0-9]{6,15}").find(member.userId)?.value
                        if (receiver == null) {
                            logger.warn(
                                    "Could not send SMS because the sender ${member.userId} didn't contain a valid telephone number." +
                                    "Usually this should never happen because the Homeserver uses the same regex."
                            )
                            Mono.empty()
                        } else {
                            logger.debug("send SMS from $roomId to $receiver")
                            smsService.sendSms(receiver = receiver, body = body)
                        }
                    }
                }.then()
    }
}