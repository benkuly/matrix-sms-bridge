package net.folivo.matrix.bridge.sms.appservice

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.membership.DefaultMembershipChangeService
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bot.membership.MatrixMembershipSyncService
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bot.user.MatrixUserService
import net.folivo.matrix.core.model.MatrixId.RoomId
import net.folivo.matrix.core.model.MatrixId.UserId
import net.folivo.matrix.restclient.MatrixClient
import org.springframework.stereotype.Service

@Service
class SmsMatrixMembershipChangeService(
    private val roomService: MatrixRoomService,
    membershipService: MatrixMembershipService,
    userService: MatrixUserService,
    membershipSyncService: MatrixMembershipSyncService,
    matrixClient: MatrixClient,
    private val botProperties: MatrixBotProperties
) : DefaultMembershipChangeService(
    roomService,
    membershipService,
    userService,
    membershipSyncService,
    matrixClient,
    botProperties
) {
    override suspend fun shouldJoinRoom(userId: UserId, roomId: RoomId): Boolean {
        if (userId == botProperties.botUserId) return super.shouldJoinRoom(userId, roomId)

        val roomAlias = roomService.getRoomAliasByRoomId(roomId)
        if (roomAlias != null && roomAlias.alias.localpart != userId.localpart) {
            return false
        }

        return super.shouldJoinRoom(userId, roomId)
    }
}