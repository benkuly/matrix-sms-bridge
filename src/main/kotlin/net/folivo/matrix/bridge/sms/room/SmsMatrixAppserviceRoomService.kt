package net.folivo.matrix.bridge.sms.room

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.folivo.matrix.appservice.api.room.CreateRoomParameter
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.membership.MembershipService
import net.folivo.matrix.bridge.sms.message.RoomMessage
import net.folivo.matrix.bridge.sms.message.RoomMessageRepository
import net.folivo.matrix.bridge.sms.user.SmsMatrixAppserviceUserService
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class SmsMatrixAppserviceRoomService(
        private val roomRepository: AppserviceRoomRepository,
        private val messageRepository: RoomMessageRepository,
        private val userService: SmsMatrixAppserviceUserService,
        private val membershipService: MembershipService,
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

    @Transactional
    override suspend fun saveRoomJoin(roomId: String, userId: String) {
        LOG.debug("saveRoomJoin in room $roomId of user $userId")
        getOrCreateRoom(roomId)
        userService.getOrCreateUser(userId)
        membershipService.getOrCreateMembership(userId, roomId)
    }

    override suspend fun saveRoomLeave(roomId: String, userId: String) { //FIXME test
        val room = getOrCreateRoom(roomId)
        val membershipsSize = membershipService.getMembershipsSizeByRoomId(roomId)
        if (membershipsSize > 1) {
            LOG.debug("save room leave in room $roomId of user $userId")
            membershipService.deleteMembership(userId, roomId)

            if (membershipService.hasRoomOnlyManagedUsersLeft(roomId)) {
                LOG.debug("leave room $roomId with all managed users because there are only managed users left")
                val memberships = membershipService.getMembershipsByRoomId(roomId)
                memberships
                        .map { it.userId }
                        .collect { joinedUserId ->
                            if (joinedUserId == "@${botProperties.username}:${botProperties.serverName}")
                                matrixClient.roomsApi.leaveRoom(roomId)
                            else matrixClient.roomsApi.leaveRoom(roomId, joinedUserId)
                        }
            }
        } else {
            LOG.debug("delete room $roomId and membership because there are no members left")
            membershipService.deleteMembership(userId, roomId)
            roomRepository.delete(room).awaitFirstOrNull()
            if (membershipService.getMembershipsSizeByUserId(userId) == 0L) {
                LOG.debug("delete user $userId because there are no memberships left")
                userService.deleteByUserId(userId)
            }
        }
    }

    suspend fun getOrCreateRoom(roomId: String): AppserviceRoom { //FIXME test
        val room = roomRepository.findById(roomId).awaitFirstOrNull()
                   ?: roomRepository.save(AppserviceRoom(roomId)).awaitFirst()
        val membershipsSize = membershipService.getMembershipsSizeByRoomId(roomId)
        if (membershipsSize == 0L) {// this is needed to get all members, e.g. when managed user joins a new room
            LOG.debug("collect all members in room $roomId because we didn't saved it yet")
            matrixClient.roomsApi.getJoinedMembers(roomId).joined.keys
                    .forEach { joinedUserId ->
                        membershipService.getOrCreateMembership(joinedUserId, roomId)
                    }
        }
        return room
    }

    suspend fun getRoom(userId: String, mappingToken: Int?): AppserviceRoom? { //FIXME test
        return if (mappingToken == null) {
            if (smsBridgeProperties.allowMappingWithoutToken) {
                val memberships = membershipService.getMembershipsByUserId(userId).take(2).toList()
                if (memberships.size == 1) getOrCreateRoom(memberships.first().roomId) else null
            } else {
                null
            }
        } else {
            return roomRepository.findByMemberAndMappingToken(userId, mappingToken).awaitFirstOrNull().let {
                if (it == null && smsBridgeProperties.allowMappingWithoutToken) {
                    val memberships = membershipService.getMembershipsByUserId(userId).take(2).toList()
                    if (memberships.size == 1) getOrCreateRoom(memberships.first().roomId) else null
                } else it
            }
        }
    }

    suspend fun getRooms(userId: String): Flow<AppserviceRoom> {
        return roomRepository.findByMember(userId).asFlow()
    }

    suspend fun syncUserAndItsRooms(userId: String? = null) { //FIXME test
        val dbUserId = userId ?: "@${botProperties.username}:${botProperties.serverName}"
        // FIXME maybe delete all joined rooms?
        if (roomRepository.findByMember(dbUserId).take(1).awaitFirstOrNull() == null) {
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

    suspend fun getRoomsWithMembers(members: Set<String>): Flow<AppserviceRoom> {
        return roomRepository.findByContainingMembers(members).asFlow()
    }

    suspend fun sendRoomMessage(message: RoomMessage, isNew: Boolean = true) {//FIXME caller should use isNew
        if (Instant.now().isAfter(message.sendAfter)) {
            val roomId = message.roomId
            val containsReceivers = membershipService.containsMembersByRoomId(roomId, message.requiredReceiverIds)
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
        } else if (isNew) { //FIXME test
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