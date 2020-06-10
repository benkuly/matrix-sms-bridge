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

    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    override fun saveRoomJoin(roomId: String, userId: String): Mono<Void> {
        LOG.debug("saveRoomJoin in room $roomId of user $userId")
        return Mono.zip(
                roomRepository.findById(roomId)
                        .switchIfEmpty(roomRepository.save(AppserviceRoom(roomId))),
                userRepository.findById(userId)
                        .switchIfEmpty(helper.isManagedUser(userId).map { AppserviceUser(userId, it) }),
                userRepository.findLastMappingTokenByUserId(userId)
                        .switchIfEmpty(Mono.just(0))
        ).flatMap {
            val room = it.t1
            val user = it.t2
            val mappingToken = it.t3 + 1

            user.rooms[room] = MemberOfProperties(mappingToken)
            userRepository.save(user)
                    .flatMap {
                        if (room.members.isEmpty()) {
                            matrixClient.roomsApi.getJoinedMembers(roomId)
                                    .flatMapMany { response -> Flux.fromIterable(response.joined.keys) }
                                    .flatMap { joinedUser -> saveRoomJoin(roomId, joinedUser) }
                                    .then()
                        } else {
                            Mono.empty()
                        }
                    }
        }
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

    fun isMemberOf(userId: String, roomId: String): Mono<Boolean> {
        return roomRepository.findById(roomId)
                .map { room ->
                    room.members.keys.find { it.userId == userId }?.let { true } ?: false
                }
    }

    fun getRoomOrCreateAndJoin(roomId: String, userId: String): Mono<AppserviceRoom> {
        return roomRepository.findById(roomId)
                .switchIfEmpty(saveRoomJoin(roomId, userId).flatMap { roomRepository.findById(roomId) })
    }
}