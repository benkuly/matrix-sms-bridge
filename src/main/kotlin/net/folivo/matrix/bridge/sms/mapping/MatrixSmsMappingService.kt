package net.folivo.matrix.bridge.sms.mapping

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.core.model.MatrixId.RoomId
import net.folivo.matrix.core.model.MatrixId.UserId
import org.springframework.stereotype.Service

@Service
class MatrixSmsMappingService(
        private val mappingRepository: MatrixSmsMappingRepository,
        private val membershipService: MatrixMembershipService,
        private val smsBridgeProperties: SmsBridgeProperties
) {

    suspend fun getOrCreateMapping(
            userId: UserId,
            roomId: RoomId
    ): MatrixSmsMapping {
        val membership = membershipService.getOrCreateMembership(userId, roomId)
        val mapping = mappingRepository.findByMembershipId(membership.id)
        return if (mapping == null) {
            val lastMappingToken = mappingRepository.findByUserIdSortByMappingTokenDesc(userId)
                                           .firstOrNull()?.mappingToken ?: 0
            mappingRepository.save(MatrixSmsMapping(membership.id, lastMappingToken + 1))
        } else mapping
    }

    suspend fun getRoomId(userId: UserId, mappingToken: Int?): RoomId? {
        return if (mappingToken == null) {
            findSingleRoomIdMapping(userId)
        } else {
            val mapping = mappingRepository.findByUserIdAndMappingToken(userId, mappingToken)
            if (mapping == null) {
                findSingleRoomIdMapping(userId)
            } else {
                membershipService.getMembership(mapping.membershipId)?.roomId
            }
        }
    }

    private suspend fun findSingleRoomIdMapping(userId: UserId): RoomId? {
        return if (smsBridgeProperties.allowMappingWithoutToken) {
            val memberships = membershipService.getMembershipsByUserId(userId).take(2).toList()
            if (memberships.size == 1) memberships.first().roomId else null
        } else null
    }
}