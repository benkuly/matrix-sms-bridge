package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.handler.AutoJoinService
import net.folivo.matrix.bridge.sms.user.AppserviceUserRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class SmsAutoJoinService(private val userRepository: AppserviceUserRepository) : AutoJoinService {
    override fun shouldJoin(roomId: String, userId: String?, isAsUser: Boolean): Mono<Boolean> {
        return if (userId != null) {
            userRepository.findById(userId)
                    .map { user ->
                        user.rooms.keys.find { it.roomId == roomId } != null
                    }
        } else {
            Mono.just(false)
        }
    }
}