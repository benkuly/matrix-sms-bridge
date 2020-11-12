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
        private val membershipService: MatrixMembershipService,
        private val userService: MatrixUserService,
        private val membershipSyncService: MatrixMembershipSyncService,
        private val matrixClient: MatrixClient,
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
        return super.shouldJoinRoom(userId, roomId) // FIXME deny join to alias from foreign user
    }
}