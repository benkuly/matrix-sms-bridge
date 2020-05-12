package net.folivo.matrix.sms.bridge.mapping

import net.folivo.matrix.bot.appservice.room.AppserviceRoomRepository
import net.folivo.matrix.bot.appservice.user.AppserviceUserRepository
import net.folivo.matrix.sms.bridge.SmsBridgeProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class SmsRoomService(
        private val smsRoomRepository: SmsRoomRepository,
        private val appserviceRoomRepository: AppserviceRoomRepository,
        private val appserviceUserRepository: AppserviceUserRepository,
        private val smsService: SmsService,
        private val smsBridgeProperties: SmsBridgeProperties
) {

    private val logger = LoggerFactory.getLogger(SmsRoomService::class.java)

    fun getBridgedSmsRoom(roomId: String, userId: String): Mono<SmsRoom> {
        return smsRoomRepository.findByRoomIdAndUserId(userId = userId, roomId = roomId)
                .switchIfEmpty(
                        Mono.zip(
                                smsRoomRepository.findLastMappingTokenByUserId(userId),
                                appserviceRoomRepository.findById(roomId),
                                appserviceUserRepository.findById(userId)
                        )
                                .flatMap { smsRoomRepository.save(SmsRoom(it.t1 + 1, it.t2, it.t3)) }
                )
    }

    fun sendSms(roomId: String, body: String, sender: String): Mono<Void> {

        return appserviceRoomRepository.findById(roomId)
                .flatMapMany { Flux.fromIterable(it.members) }
                .flatMap { member ->
                    getBridgedSmsRoom(roomId, member.userId).map {
                        smsBridgeProperties.templates.outgoingMessage
                                .replace("{username}", sender)
                                .replace("{body}", body)
                                .replace("{token}", "#" + it.mappingToken.toString())
                    }
                            .flatMap {
                                val receiver = Regex("\\+[0-9]{6,15}").find(member.userId)?.value
                                if (receiver == null) {
                                    logger.warn(
                                            "Could not send SMS because the sender ${member.userId} didn't contain a valid telephone number." +
                                            "Usually this should never happen because the Homeserver uses the same regex."
                                    )
                                    Mono.empty()
                                } else {
                                    smsService.sendSms(receiver, it)
                                }
                            }
                }.then()
    }

    fun handleSms(body: String, sender: String): Mono<Void> {

    }
}