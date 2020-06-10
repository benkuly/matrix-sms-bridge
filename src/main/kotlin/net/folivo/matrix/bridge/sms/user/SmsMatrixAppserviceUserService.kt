package net.folivo.matrix.bridge.sms.user

import net.folivo.matrix.appservice.api.user.CreateUserParameter
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class SmsMatrixAppserviceUserService(
        private val helper: MatrixAppserviceServiceHelper,
        private val appserviceUserRepository: AppserviceUserRepository
) : MatrixAppserviceUserService {

    override fun userExistingState(userId: String): Mono<MatrixAppserviceUserService.UserExistingState> {
        return appserviceUserRepository.existsById(userId)
                .flatMap { isInDatabase ->
                    if (isInDatabase) {
                        Mono.just(MatrixAppserviceUserService.UserExistingState.EXISTS)
                    } else {
                        helper.isManagedUser(userId)
                                .map { shouldCreateUser ->
                                    if (shouldCreateUser) {
                                        MatrixAppserviceUserService.UserExistingState.CAN_BE_CREATED
                                    } else {
                                        MatrixAppserviceUserService.UserExistingState.DOES_NOT_EXISTS
                                    }
                                }
                    }
                }
    }

    override fun getCreateUserParameter(userId: String): Mono<CreateUserParameter> {
        return Mono.create {
            val telephoneNumber = userId.removePrefix("@sms_").substringBefore(":")
            val displayName = "+$telephoneNumber (SMS)"
            it.success(CreateUserParameter(displayName))
        }
    }

    override fun saveUser(userId: String): Mono<Void> {
        return helper.isManagedUser(userId)
                .flatMap { appserviceUserRepository.save(AppserviceUser(userId, it)) }
                .then()
    }

    fun getRoomId(userId: String, mappingToken: Int?): Mono<String> {
        return appserviceUserRepository.findById(userId)
                .flatMap { user ->
                    Mono.justOrEmpty(
                            user.rooms.entries
                                    .find { it.value.mappingToken == mappingToken }
                                    ?.key?.roomId)
                }
    }
}