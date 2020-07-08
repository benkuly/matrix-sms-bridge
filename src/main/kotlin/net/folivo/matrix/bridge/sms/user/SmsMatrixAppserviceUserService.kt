package net.folivo.matrix.bridge.sms.user

import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.folivo.matrix.appservice.api.user.CreateUserParameter
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService.UserExistingState
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService.UserExistingState.*
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import net.folivo.matrix.bot.config.MatrixBotProperties
import org.springframework.stereotype.Service

@Service
class SmsMatrixAppserviceUserService(
        private val helper: MatrixAppserviceServiceHelper,
        private val userRepository: AppserviceUserRepository,
        private val botProperties: MatrixBotProperties
) : MatrixAppserviceUserService {

    override suspend fun userExistingState(userId: String): UserExistingState {
        val userExists = userRepository.existsById(userId).awaitFirst()
        return if (userExists) {
            EXISTS
        } else {
            if (helper.isManagedUser(userId)) CAN_BE_CREATED else DOES_NOT_EXISTS
        }
    }

    override suspend fun getCreateUserParameter(userId: String): CreateUserParameter {
        return if (userId == "@${botProperties.username}:${botProperties.serverName}") {
            CreateUserParameter("SMS Bot")
        } else {
            val telephoneNumber = userId.removePrefix("@sms_").substringBefore(":")
            val displayName = "+$telephoneNumber (SMS)"
            CreateUserParameter(displayName)
        }
    }

    override suspend fun saveUser(userId: String) {
        
    }

    suspend fun getUser(userId: String): AppserviceUser {
        return userRepository.findById(userId).awaitFirstOrNull()
               ?: helper.isManagedUser(userId)
                       .let { userRepository.save(AppserviceUser(userId, it)).awaitFirst() }
    }

    suspend fun getLastMappingToken(userId: String): Int {
        return userRepository.findLastMappingTokenByUserId(userId).awaitFirstOrDefault(0)
    }
}