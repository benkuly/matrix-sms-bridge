package net.folivo.matrix.bridge.sms.room

import net.folivo.matrix.appservice.api.room.CreateRoomParameter
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import net.folivo.matrix.bot.config.MatrixBotProperties
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
        private val helper: MatrixAppserviceServiceHelper,
        private val botProperties: MatrixBotProperties
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

    private fun findOrCreateUser(userId: String): Mono<AppserviceUser> {
        return userRepository.findById(userId)
                .switchIfEmpty(
                        helper.isManagedUser(userId)
                                .flatMap { userRepository.save(AppserviceUser(userId, it)) })
    }

    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    override fun saveRoomLeave(roomId: String, userId: String): Mono<Void> {
        LOG.debug("saveRoomLeave in room $roomId of user $userId") // TODO remove old rooms
        return roomRepository.findById(roomId)
                .flatMap { room ->
                    val user = room.members.keys.find { it.userId == userId }
                    if (user != null) {
                        room.members.remove(user)

                        Flux.fromIterable(room.members.keys)
                                .map { it.isManaged }
                                .collectList()
                                .map { !it.contains(false) }
                                .flatMap { onlyManagedUsers ->
                                    if (onlyManagedUsers) {
                                        Flux.fromIterable(room.members.keys)
                                                .flatMap {
                                                    if (it.userId == "@${botProperties.username}:${botProperties.serverName}")
                                                        matrixClient.roomsApi.leaveRoom(roomId)
                                                    else matrixClient.roomsApi.leaveRoom(roomId, it.userId)
                                                }
                                                .then(roomRepository.delete(room))
                                    } else {
                                        roomRepository.save(room)
                                    }
                                }
                    } else {
                        Mono.empty()
                    }
                }.then()
    }

    fun isMemberOf(userId: String, roomId: String): Mono<Boolean> {
        return roomRepository.findById(roomId)
                .map { room ->
                    room.members.keys.find { it.userId == userId }?.let { true } ?: false
                }
    }


    fun getRoomOrCreateAndJoin(roomId: String, userId: String): Mono<AppserviceRoom> {
        return roomRepository.findById(roomId)
                .switchIfEmpty(Mono.defer { saveRoomJoinAndGet(roomId, userId) })
    }
}