package net.folivo.matrix.bridge.sms.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
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

    suspend fun getOrCreateUser(userId: String): AppserviceUser {
        return userRepository.findById(userId).awaitFirstOrNull()
               ?: helper.isManagedUser(userId)
                       .let { userRepository.save(AppserviceUser(userId, it)).awaitFirst() }
    }

    suspend fun deleteByUserId(userId: String) {
        userRepository.deleteById(userId).awaitFirst()
    }

    suspend fun deleteAllUsers() {
        userRepository.deleteAll().awaitFirstOrNull()
    }

    suspend fun getUsersByRoomId(roomId: String): Flow<AppserviceUser> {
        return userRepository.findByRoomId(roomId).asFlow()
    }
}