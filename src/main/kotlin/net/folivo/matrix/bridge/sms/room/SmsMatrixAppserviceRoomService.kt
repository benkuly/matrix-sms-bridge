package net.folivo.matrix.bridge.sms.room

import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.folivo.matrix.appservice.api.room.CreateRoomParameter
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.bridge.sms.user.SmsMatrixAppserviceUserService
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SmsMatrixAppserviceRoomService(
        private val roomRepository: AppserviceRoomRepository,
        private val userService: SmsMatrixAppserviceUserService,
        private val matrixClient: MatrixClient,
        private val botProperties: MatrixBotProperties
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
        val room = getRoom(roomId, userId)
        LOG.debug("saveRoomJoin in room $roomId of user $userId")
        val user = userService.getUser(userId)

        if (!room.members.containsKey(user)) {
            val mappingToken = userService.getLastMappingToken(userId)

            room.members[user] = MemberOfProperties(mappingToken + 1)
            roomRepository.save(room).awaitFirst()
        }
    }

    override suspend fun saveRoomLeave(roomId: String, userId: String) {
        LOG.debug("saveRoomLeave in room $roomId of user $userId") // TODO remove old rooms
        val room = getRoom(roomId, userId)
        val user = room.members.keys.find { it.userId == userId }
        if (user != null) {
            room.members.remove(user)

            val hasOnlyManagedUsers = !room.members.keys
                    .map { it.isManaged }
                    .contains(false)
            if (hasOnlyManagedUsers) {
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

//    fun isMemberOf(userId: String, roomId: String): Boolean { //FIXME old?
//        val room = roomRepository.findById(roomId)
//        return room.members.keys.find { it.userId == userId }?.let { true } ?: false
//    }

    suspend fun getRoom(roomId: String, userId: String): AppserviceRoom {
        val room = roomRepository.findById(roomId).awaitFirstOrNull()
                   ?: roomRepository.save(AppserviceRoom(roomId)).awaitFirst()
        if (room.members.isEmpty()) {
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
}