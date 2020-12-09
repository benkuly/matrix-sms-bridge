package net.folivo.matrix.bridge.sms.appservice

import net.folivo.matrix.appservice.api.user.RegisterUserParameter
import net.folivo.matrix.bot.appservice.DefaultAppserviceUserService
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.user.MatrixUserService
import net.folivo.matrix.bot.util.BotServiceHelper
import net.folivo.matrix.core.model.MatrixId.UserId
import org.springframework.stereotype.Service

@Service
class SmsMatrixAppserviceUserService(
    userService: MatrixUserService,
    helper: BotServiceHelper,
    private val botProperties: MatrixBotProperties
) : DefaultAppserviceUserService(userService, helper, botProperties) {

    override suspend fun getRegisterUserParameter(userId: UserId): RegisterUserParameter {
        return if (userId == botProperties.botUserId) {
            RegisterUserParameter("SMS Bot")
        } else {
            val telephoneNumber = userId.localpart.removePrefix("sms_")
            val displayName = "+$telephoneNumber (SMS)"
            RegisterUserParameter(displayName)
        }
    }
}