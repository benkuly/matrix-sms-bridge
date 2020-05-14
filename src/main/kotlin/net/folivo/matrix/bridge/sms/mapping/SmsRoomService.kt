package net.folivo.matrix.bridge.sms.mapping

import net.folivo.matrix.bot.appservice.room.AppserviceRoomRepository
import net.folivo.matrix.bot.appservice.user.AppserviceUserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class SmsRoomService(
        private val smsRoomRepository: SmsRoomRepository,
        private val appserviceRoomRepository: AppserviceRoomRepository,
        private val appserviceUserRepository: AppserviceUserRepository
) {

    private val logger = LoggerFactory.getLogger(SmsRoomService::class.java)

    fun getBridgedSmsRoom(roomId: String, userId: String): Mono<SmsRoom> {
        return smsRoomRepository.findByRoomIdAndUserId(roomId = roomId, userId = userId)
                .switchIfEmpty(
                        Mono.defer {
                            Mono.zip(
                                    smsRoomRepository.findLastMappingTokenByUserId(userId)
                                            .switchIfEmpty(Mono.just(0)),
                                    appserviceUserRepository.findById(userId),
                                    appserviceRoomRepository.findById(roomId)

                            )
                        }.flatMap {
                            smsRoomRepository.save(
                                    SmsRoom(
                                            it.t1 + 1,
                                            it.t2,
                                            it.t3
                                    )
                            )
                        }
                )
    }
}