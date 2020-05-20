package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.handler.AutoJoinService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class NoAutoJoinForAsUser(private val smsBridgeProperties: SmsBridgeProperties) : AutoJoinService {
    override fun shouldJoin(roomId: String, userId: String?, isAsUser: Boolean): Mono<Boolean> {
        return if (isAsUser && roomId != smsBridgeProperties.defaultRoomId) Mono.just(false) else Mono.just(true)
    }
}