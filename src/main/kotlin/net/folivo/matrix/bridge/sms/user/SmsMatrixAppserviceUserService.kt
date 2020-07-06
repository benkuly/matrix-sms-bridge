package net.folivo.matrix.bridge.sms.user

import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.folivo.matrix.appservice.api.user.CreateUserParameter
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService.UserExistingState
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService.UserExistingState.*
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import org.springframework.stereotype.Service

@Service
class SmsMatrixAppserviceUserService(
        private val helper: MatrixAppserviceServiceHelper,
        private val userRepository: AppserviceUserRepository,
        private val appserviceUserRepository: AppserviceUserRepository,
        private val smsBridgeProperties: SmsBridgeProperties
) : MatrixAppserviceUserService {

    override suspend fun userExistingState(userId: String): UserExistingState {
        val userExists = appserviceUserRepository.existsById(userId).awaitFirst()
        return if (userExists) {
            EXISTS
        } else {
            if (helper.isManagedUser(userId)) CAN_BE_CREATED else DOES_NOT_EXISTS
        }
    }

    override suspend fun getCreateUserParameter(userId: String): CreateUserParameter {
        val telephoneNumber = userId.removePrefix("@sms_").substringBefore(":")
        val displayName = "+$telephoneNumber (SMS)"
        return CreateUserParameter(displayName)
    }

    override suspend fun saveUser(userId: String) {
        val userExists = appserviceUserRepository.existsById(userId).awaitFirst()
        if (!userExists) {
            helper.isManagedUser(userId)
                    .let { appserviceUserRepository.save(AppserviceUser(userId, it)).awaitFirst() }
        }
    }

    suspend fun getRoomId(userId: String, mappingToken: Int?): String? {
        val user = getUser(userId)
        val rooms = user.rooms.keys
        return if (rooms.size == 1 && smsBridgeProperties.allowMappingWithoutToken) {
            rooms.first().roomId
        } else {
            user.rooms.entries
                    .find { it.value.mappingToken == mappingToken }
                    ?.key?.roomId
        }
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