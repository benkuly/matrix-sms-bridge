package net.folivo.matrix.bridge.sms.room

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
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

        if (!room.members.map { it.member }.contains(user)) {
            val mappingToken = userService.getLastMappingToken(userId)

            val newRoom = room.copy(members = room.members.plus(MemberOfProperties(user, mappingToken + 1)))
            roomRepository.save(newRoom).awaitFirstOrNull()
        }
    }

    override suspend fun saveRoomLeave(roomId: String, userId: String) {
        val room = getOrCreateRoom(roomId)
        val memberOf = room.members.find { it.member.userId == userId }
        if (memberOf != null) {
            if (room.members.size > 1) {
                LOG.debug("save room leave in room $roomId of user $userId")
                val newRoom = room.copy(members = room.members.minus(memberOf))
                roomRepository.save(newRoom).awaitFirstOrNull()

                val hasOnlyManagedUsersLeft = !room.members
                        .map { it.member.isManaged }
                        .contains(false)
                if (hasOnlyManagedUsersLeft) {
                    LOG.debug("leave room $roomId with all managed users because there are only managed users left")

                    newRoom.members
                            .map {
                                if (it.member.userId == "@${botProperties.username}:${botProperties.serverName}")
                                    matrixClient.roomsApi.leaveRoom(roomId)
                                else matrixClient.roomsApi.leaveRoom(roomId, it.member.userId)
                            }
                }
            } else {
                LOG.debug("delete room $roomId because there are no members left")
                roomRepository.delete(room).awaitFirstOrNull()
            }
        }
    }

    suspend fun getOrCreateRoom(roomId: String): AppserviceRoom {
        val room = roomRepository.findById(roomId).awaitFirstOrNull()
                   ?: roomRepository.save(AppserviceRoom(roomId)).awaitFirst()
        if (room.members.isEmpty()) {// this is needed to get all members, e.g. when managed user joins a new room
            LOG.debug("collect all members in room $roomId because we didn't save it yet")
            val members = matrixClient.roomsApi.getJoinedMembers(roomId).joined.keys
                    .map { joinedUserId ->
                        val user = userService.getUser(joinedUserId)
                        val mappingToken = userService.getLastMappingToken(joinedUserId)
                        Pair(user, mappingToken)
                    }.map { (user, mappingToken) ->
                        LOG.debug("collect user ${user.userId} to room $roomId")
                        MemberOfProperties(user, mappingToken + 1)
                    }
            val newRoom = room.copy(members = members)
            LOG.debug("save room $roomId")
            return roomRepository.save(newRoom).awaitFirst()
        }
        return room
    }

    suspend fun getRoom(userId: String, mappingToken: Int?): AppserviceRoom? {
        return if (mappingToken == null) {
            if (smsBridgeProperties.allowMappingWithoutToken) {
                val rooms = roomRepository.findAllByUserId(userId).take(2).asFlow().toList()
                if (rooms.size == 1) rooms.first() else null
            } else {
                null
            }
        } else {
            return roomRepository.findByUserIdAndMappingToken(userId, mappingToken).awaitFirstOrNull().let {
                if (it == null && smsBridgeProperties.allowMappingWithoutToken) {
                    val rooms = roomRepository.findAllByUserId(userId).take(2).asFlow().toList()
                    if (rooms.size == 1) rooms.first() else null
                } else it
            }
        }
    }

    suspend fun getRooms(userId: String): Flow<AppserviceRoom> {
        return roomRepository.findAllByUserId(userId).asFlow()
    }

    suspend fun syncUserAndItsRooms(userId: String? = null) {
        val dbUserId = userId ?: "@${botProperties.username}:${botProperties.serverName}"
        if (roomRepository.findAllByUserId(dbUserId).take(1).awaitFirstOrNull() == null) {
            try {
                matrixClient.roomsApi.getJoinedRooms(asUserId = userId)
                        .collect { room ->
                            matrixClient.roomsApi.getJoinedMembers(
                                    room,
                                    asUserId = userId
                            ).joined.keys.forEach { user ->
                                saveRoomJoin(room, user)
                            }
                        }
                LOG.debug("synced user because we didn't know any rooms with him")
            } catch (error: Throwable) {
                LOG.debug("tried to sync user without rooms, but that was not possible: ${error.message}")
            }
        }
    }

    suspend fun getRoomsWithUsers(members: Set<String>): Flow<AppserviceRoom> {
        return roomRepository.findByMembersUserIdContaining(members).asFlow()
    }

    suspend fun sendRoomMessage(message: RoomMessage) {
        if (Instant.now().isAfter(message.sendAfter)) {
            val containsReceivers = message.room.members.map { it.member.userId }
                    .containsAll(message.requiredReceiverIds)
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
                    messageRepository.delete(message).awaitFirstOrNull()
                } catch (error: Throwable) {
                    LOG.debug(
                            "Could not send cached message to room $roomId. This happens e.g. when the bot was kicked " +
                            "out of the room, before the required receivers did join. Error: ${error.message}"
                    )
                    if (message.sendAfter.until(Instant.now(), ChronoUnit.DAYS) > 3) {
                        LOG.warn(
                                "We have cached messages for the room $roomId, but sending the message " +
                                "didn't worked since 3 days. " +
                                "This usually should never happen! The message will now be deleted."
                        )
                        messageRepository.delete(message).awaitFirstOrNull()
                        // TODO directly notify user
                    }
                }
            } else if (!isNew && message.sendAfter.until(Instant.now(), ChronoUnit.DAYS) > 3) {
                LOG.warn(
                        "We have cached messages for the room $roomId, but the required receivers " +
                        "${message.requiredReceiverIds.joinToString()} didn't join since 3 days. " +
                        "This usually should never happen! The message will now be deleted."
                )
                messageRepository.delete(message).awaitFirstOrNull()
                // TODO directly notify user
            } else if (isNew) {
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

    suspend fun deleteAllRooms() {
        roomRepository.deleteAll().awaitFirstOrNull()
    }
}