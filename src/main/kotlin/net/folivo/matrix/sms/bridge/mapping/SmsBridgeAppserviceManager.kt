package net.folivo.matrix.sms.bridge.mapping

import net.folivo.matrix.appservice.api.user.CreateUserParameter
import net.folivo.matrix.bot.appservice.DefaultAppserviceBotManager
import reactor.core.publisher.Mono

class SmsBridgeAppserviceManager : DefaultAppserviceBotManager() {

    override fun getCreateUserParameter(matrixUserId: String): Mono<CreateUserParameter> {
        return Mono.fromCallable {
            val username = matrixUserId.trimStart('@').substringBefore(":")
            CreateUserParameter(displayName = "$username (SMS)")
        }
    }
}