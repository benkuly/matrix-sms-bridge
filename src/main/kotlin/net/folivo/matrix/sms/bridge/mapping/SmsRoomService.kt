package net.folivo.matrix.sms.bridge.mapping

import net.folivo.matrix.bot.appservice.room.AppserviceRoomRepository
import net.folivo.matrix.bot.appservice.room.SmsRoom
import net.folivo.matrix.bot.appservice.room.SmsRoomRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class SmsRoomService(
        private val smsRoomRepository: SmsRoomRepository,
        private val appserviceRoomRepository: AppserviceRoomRepository
) {
    fun bridgeRoom(roomId: String): Mono<SmsRoom> {
        return appserviceRoomRepository.findById(roomId)
                .flatMap { appserviceRoom ->
                    smsRoomRepository.findByBridgedRoom(appserviceRoom)
                            .switchIfEmpty(smsRoomRepository.save(SmsRoom(createMappingToken(roomId), appserviceRoom)))
                }
    }

    fun createMappingToken(roomId: String): String {

    }

    fun sendSms(roomId: String, body: String): Mono<Void> {

    }
}