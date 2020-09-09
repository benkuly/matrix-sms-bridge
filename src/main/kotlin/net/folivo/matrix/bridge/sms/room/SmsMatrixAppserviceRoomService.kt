package net.folivo.matrix.bridge.sms.room

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.folivo.matrix.appservice.api.room.CreateRoomParameter
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.bridge.sms.user.SmsMatrixAppserviceUserService
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class SmsMatrixAppserviceRoomService(
        private val roomRepository: AppserviceRoomRepository,
        private val messageRepository: RoomMessageRepository,
        private val userService: SmsMatrixAppserviceUserService,
        private val matrixClient: MatrixClient,
        private val botProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties
) : MatrixAppserviceRoomService {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override suspend fun roomExistingState(roomAlias: String): RoomExistingState {
        return DOES_NOT_EXISTS
    }

    override suspend fun getCreateRoomParameter(roomAlias: String): CreateRoomParameter {
        return CreateRoomParameter()
    }

    override suspend fun saveRoom(roomAlias: String, roomId: String) {

    }

    override suspend fun saveRoomJoin(roomId: String, userId: String) {
        val room = getOrCreateRoom(roomId)
        LOG.debug("saveRoomJoin in room $roomId of user $userId")
        val user = userService.getUser(userId)

        if (!room.members.containsKey(user)) {
            val mappingToken = userService.getLastMappingToken(userId)

            room.members[user] = MemberOfProperties(mappingToken + 1)
            roomRepository.save(room).awaitFirst()
        }
    }

    // FIXME why this is not working properly
    override suspend fun saveRoomLeave(roomId: String, userId: String) {
        LOG.debug("saveRoomLeave in room $roomId of user $userId")
        val room = getOrCreateRoom(roomId)
        val user = room.members.keys.find { it.userId == userId }
        if (user != null) {
            room.members.remove(user)

            val hasOnlyManagedUsersLeft = !room.members.keys
                    .map { it.isManaged }
                    .contains(false)
            if (hasOnlyManagedUsersLeft) {
                room.members.keys
                        .map {
                            if (it.userId == "@${botProperties.username}:${botProperties.serverName}")
                                matrixClient.roomsApi.leaveRoom(roomId)
                            else matrixClient.roomsApi.leaveRoom(roomId, it.userId)
                        }
                roomRepository.delete(room)
            } else {
                roomRepository.save(room)
            }
        }
    }

    suspend fun getOrCreateRoom(roomId: String): AppserviceRoom {
        val room = roomRepository.findById(roomId).awaitFirstOrNull()
                   ?: roomRepository.save(AppserviceRoom(roomId)).awaitFirst()
        if (room.members.isEmpty()) {// this is needed to get all members, e.g. when managed user joins a new room
            LOG.debug("collect all members in room $roomId because we didn't save it yet")
            matrixClient.roomsApi.getJoinedMembers(roomId).joined.keys
                    .map { joinedUserId ->
                        val user = userService.getUser(joinedUserId)
                        val mappingToken = userService.getLastMappingToken(joinedUserId)
                        Pair(user, mappingToken)
                    }.forEach { (user, mappingToken) ->
                        room.members[user] = MemberOfProperties(mappingToken + 1)
                    }
            return roomRepository.save(room).awaitFirst()
        }
        return room
    }

    suspend fun getRoom(userId: String, mappingToken: Int?): AppserviceRoom? {
        val user = userService.getUser(userId)
        val rooms = user.rooms.keys
        return if (rooms.size == 1 && smsBridgeProperties.allowMappingWithoutToken) {
            rooms.first()
        } else {
            user.rooms.entries
                    .find { it.value.mappingToken == mappingToken }
                    ?.key
        }
    }

    suspend fun getRoomsWithUsers(members: Set<String>): Flow<AppserviceRoom> {
        return roomRepository.findByMembersUserIdContaining(members).asFlow()
    }

    // FIXME test
    suspend fun sendRoomMessage(message: RoomMessage) {
        if (Instant.now().isAfter(message.sendAfter)) {
            val containsReceivers = message.room.members.keys.map { it.userId }.containsAll(message.requiredReceiverIds)
            val roomId = message.room.roomId
            val isNew = message.id == null
            if (containsReceivers) {
                try {
                    matrixClient.roomsApi.sendRoomEvent(
                            roomId = roomId,
                            eventContent = if (message.isNotice) NoticeMessageEventContent(message.body)
                            else TextMessageEventContent(message.body)
                    )
                    LOG.debug("sent cached message to room $roomId")
                } catch (error: MatrixServerException) {
                    LOG.warn(
                            "Could not send cached message to room $roomId. This happens e.g. when the bot was kicked " +
                            "out of the room, before the required receivers did join. Error: ${error.message}"
                    )
                    // TODO directly notify user
                } catch (error: Throwable) {
                    LOG.warn(
                            "Could not send cached message to room $roomId. Error: ${error.message}"
                    )
                } finally {
                    messageRepository.delete(message).awaitFirstOrNull()
                }
            } else if (!isNew && message.sendAfter.until(Instant.now(), ChronoUnit.DAYS) > 3) {
                LOG.warn(
                        "We have cached messages for the room $roomId, but the required receivers " +
                        "${message.requiredReceiverIds.joinToString()} didn't join since 3 days. " +
                        "This usually should never happen! The message will now be deleted."
                )
                messageRepository.delete(message).awaitFirstOrNull()
            } else if (isNew) {
                // prevent, that messages with sendAfter in large future are deleted, although it was send a few minutes ago
                messageRepository.save(message.copy(sendAfter = Instant.now())).awaitFirst()
            } else {
                LOG.debug("wait for required receivers to join")
            }
        } else {
            messageRepository.save(message).awaitFirst()
        }
    }

    suspend fun processMessageQueue() {
        messageRepository.findAll().asFlow().collect { sendRoomMessage(it) }
    }
}