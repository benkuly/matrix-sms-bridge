package net.folivo.matrix.bridge.sms.room

import net.folivo.matrix.appservice.api.room.CreateRoomParameter
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.AppserviceUserRepository
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.restclient.MatrixClient
import org.neo4j.springframework.data.repository.config.ReactiveNeo4jRepositoryConfigurationExtension
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class SmsMatrixAppserviceRoomService(
        private val roomRepository: AppserviceRoomRepository,
        private val userRepository: AppserviceUserRepository,
        private val matrixClient: MatrixClient,
        private val helper: MatrixAppserviceServiceHelper
) : MatrixAppserviceRoomService {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override fun roomExistingState(roomAlias: String): Mono<RoomExistingState> {
        return Mono.just(DOES_NOT_EXISTS)
    }

    override fun getCreateRoomParameter(roomAlias: String): Mono<CreateRoomParameter> {
        return Mono.just(CreateRoomParameter())
    }

    override fun saveRoom(roomAlias: String, roomId: String): Mono<Void> {
        return Mono.empty()
    }

    override fun saveRoomJoin(roomId: String, userId: String): Mono<Void> {
        return saveRoomJoinAndGet(roomId, userId).then()
    }

    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    fun saveRoomJoinAndGet(roomId: String, userId: String): Mono<AppserviceRoom> {
        return roomRepository.findById(roomId)
                .switchIfEmpty(roomRepository.save((AppserviceRoom(roomId))))
                .flatMap { room ->
                    LOG.debug("saveRoomJoin in room $roomId of user $userId")
                    if (room.members.isEmpty()) {
                        LOG.debug("collect all members in room $roomId because we didn't save it yet")
                        matrixClient.roomsApi.getJoinedMembers(roomId)
                                .flatMapMany { response -> Flux.fromIterable(response.joined.keys) }
                                .flatMap { joinedUserId ->
                                    Mono.zip(
                                            findOrCreateUser(joinedUserId),
                                            userRepository.findLastMappingTokenByUserId(joinedUserId)
                                                    .switchIfEmpty(Mono.just(0))
                                    )
                                }.collectList()
                                .flatMap { members ->
                                    members.forEach { room.members[it.t1] = MemberOfProperties(it.t2 + 1) }
                                    roomRepository.save(room)
                                }
                    } else {
                        LOG.debug("save single join in room $roomId")
                        Mono.zip(
                                findOrCreateUser(userId),
                                userRepository.findLastMappingTokenByUserId(userId)
                                        .switchIfEmpty(Mono.just(0))
                        ).flatMap {
                            val user = it.t1
                            val mappingToken = it.t2 + 1

                            room.members[user] = MemberOfProperties(mappingToken)
                            roomRepository.save(room)
                        }
                    }
                }
    }

    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    private fun findOrCreateUser(userId: String): Mono<AppserviceUser> {
        return userRepository.findById(userId)
                .switchIfEmpty(
                        helper.isManagedUser(userId)
                                .flatMap { userRepository.save(AppserviceUser(userId, it)) })
    }

    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    override fun saveRoomLeave(roomId: String, userId: String): Mono<Void> {
        LOG.debug("saveRoomLeave in room $roomId of user $userId") // TODO remove old rooms
        return userRepository.findById(userId)
                .flatMap { user ->
                    val room = user.rooms.keys.find { it.roomId == roomId }
                    if (room != null) {
                        user.rooms.remove(room)
                        userRepository.save(user)
                    } else {
                        Mono.empty()
                    }
                }.then()
    }

    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    fun isMemberOf(userId: String, roomId: String): Mono<Boolean> {
        return roomRepository.findById(roomId)
                .map { room ->
                    room.members.keys.find { it.userId == userId }?.let { true } ?: false
                }
    }


    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    fun getRoomOrCreateAndJoin(roomId: String, userId: String): Mono<AppserviceRoom> {
        return roomRepository.findById(roomId)
                .switchIfEmpty(Mono.defer { saveRoomJoinAndGet(roomId, userId) })
    }
}