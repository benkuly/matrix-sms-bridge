package net.folivo.matrix.bridge.sms.user

import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.folivo.matrix.appservice.api.user.CreateUserParameter
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService.UserExistingState
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService.UserExistingState.*
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import org.springframework.stereotype.Service

@Service
class SmsMatrixAppserviceUserService(
        private val helper: MatrixAppserviceServiceHelper,
        private val userRepository: AppserviceUserRepository
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
        val telephoneNumber = userId.removePrefix("@sms_").substringBefore(":")
        val displayName = "+$telephoneNumber (SMS)"
        return CreateUserParameter(displayName)
    }

    override suspend fun saveUser(userId: String) {
        if (!userExists(userId)) {
            helper.isManagedUser(userId)
                    .let { userRepository.save(AppserviceUser(userId, it)).awaitFirst() }
        }
    }

    // FIXME test
    suspend fun userExists(userId: String): Boolean {
        return userRepository.existsById(userId).awaitFirst()
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