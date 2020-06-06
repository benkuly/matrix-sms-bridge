package net.folivo.matrix.bridge.sms.room

import net.folivo.matrix.appservice.api.room.CreateRoomParameter
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.AppserviceUserRepository
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.neo4j.springframework.data.repository.config.ReactiveNeo4jRepositoryConfigurationExtension
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

@Service
class SmsMatrixAppserviceRoomService(
        private val roomRepository: AppserviceRoomRepository,
        private val userRepository: AppserviceUserRepository,
        private val matrixClient: MatrixClient,
        private val botProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties
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
                        .switchIfEmpty(Mono.just(AppserviceUser(userId))),
                userRepository.findLastMappingTokenByUserId(userId)
                        .switchIfEmpty(Mono.just(0))
        ).flatMap {
            val room = it.t1
            val user = it.t2
            val mappingToken = it.t3 + 1
            user.rooms[room] = MemberOfProperties(mappingToken)
            userRepository.save(user)
        }.then()
    }

    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    override fun saveRoomLeave(roomId: String, userId: String): Mono<Void> {
        LOG.debug("saveRoomLeave in room $roomId of user $userId")
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

    // FIXME test
    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    fun createRoomAndSendMessage(
            body: String,
            sender: String,
            receiverNumber: Long,
            roomName: String?,
            createNewRoom: Boolean,
            disableAutomaticRoomCreation: Boolean
    ): Mono<String> {
        val receiverId = "@sms_$receiverNumber:${botProperties.serverName}"
        val members = listOf(sender, receiverId)
        return roomRepository.findByMembersKeyUserIdContaining(members)
                .limitRequest(2)
                .collectList()
                .flatMap { rooms ->
                    if (rooms.size == 0 && !disableAutomaticRoomCreation || createNewRoom) {
                        matrixClient.roomsApi.createRoom(name = roomName, invite = members)
                                .delayUntil { roomId ->
                                    matrixClient.roomsApi.getJoinedMembers(roomId)
                                            .flatMap {
                                                if (it.joined.keys.contains(receiverId)) {
                                                    Mono.just(roomId)
                                                } else {
                                                    Mono.error(
                                                            MatrixServerException(
                                                                    NOT_FOUND,
                                                                    ErrorResponse(
                                                                            "NET_FOLIVO.NOT_FOUND",
                                                                            "Receiver $receiverId didn't join the room $roomId yet."
                                                                    )
                                                            )
                                                    )
                                                }
                                            }.retryWhen(Retry.backoff(5, Duration.ofMillis(500)))
                                }
                                .flatMap { roomId ->
                                    matrixClient.roomsApi.sendRoomEvent(
                                            roomId = roomId,
                                            eventContent = TextMessageEventContent(
                                                    smsBridgeProperties.templates.botSmsSendNewRoomMessage
                                                            .replace("{sender}", sender)
                                                            .replace("{body}", body)
                                            )
                                    )
                                }.map { smsBridgeProperties.templates.botSmsSendCreatedRoomAndSendMessage }
                    } else if (rooms.size == 1) {
                        matrixClient.roomsApi.sendRoomEvent(
                                roomId = rooms[0].roomId,
                                eventContent = TextMessageEventContent(
                                        smsBridgeProperties.templates.defaultRoomIncomingMessage
                                                .replace("{sender}", sender)
                                                .replace("{body}", body)
                                )
                        ).map { smsBridgeProperties.templates.botSmsSendSendMessage }
                    } else if (rooms.size > 1) {
                        Mono.just(smsBridgeProperties.templates.botSmsSendTooManyRooms)
                    } else {
                        Mono.just(smsBridgeProperties.templates.botSmsSendNoSendMessage)
                    }
                }
    }
}