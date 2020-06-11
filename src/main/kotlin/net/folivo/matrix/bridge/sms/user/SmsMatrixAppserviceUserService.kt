package net.folivo.matrix.bridge.sms.user

import net.folivo.matrix.appservice.api.user.CreateUserParameter
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import org.neo4j.springframework.data.repository.config.ReactiveNeo4jRepositoryConfigurationExtension
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Service
class SmsMatrixAppserviceUserService(
        private val helper: MatrixAppserviceServiceHelper,
        private val appserviceUserRepository: AppserviceUserRepository,
        private val smsBridgeProperties: SmsBridgeProperties
) : MatrixAppserviceUserService {

    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
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

    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    override fun saveUser(userId: String): Mono<Void> {
        return helper.isManagedUser(userId)
                .flatMap { appserviceUserRepository.save(AppserviceUser(userId, it)) }
                .then()
    }

    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    fun getRoomId(userId: String, mappingToken: Int?): Mono<String> {
        return appserviceUserRepository.findById(userId)
                .flatMap { user ->
                    val rooms = user.rooms.keys
                    if (rooms.size == 1 && smsBridgeProperties.allowMappingWithoutToken) {
                        Mono.just(rooms.first().roomId)
                    } else {
                        Mono.justOrEmpty(
                                user.rooms.entries
                                        .find { it.value.mappingToken == mappingToken }
                                        ?.key?.roomId)
                    }
                }
    }
}