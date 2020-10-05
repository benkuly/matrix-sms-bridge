package net.folivo.matrix.bridge.sms.membership

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.springframework.stereotype.Service

@Service
class MembershipService(private val membershipRepository: MembershipRepository) {

    suspend fun getOrCreateMembership(userId: String, roomId: String): Membership {//FIXME test
        val membership = membershipRepository.findByUserIdAndRoomId(userId, roomId).awaitFirstOrNull()
        return if (membership == null) {
            val lastMappingToken = membershipRepository.findByUserIdSortByMappingTokenDesc(userId)
                                           .awaitFirstOrNull()?.mappingToken ?: 0
            membershipRepository.save(Membership(userId, roomId, lastMappingToken + 1)).awaitFirst()
        } else membership
    }

    suspend fun getMembershipsByRoomId(roomId: String): Flow<Membership> {
        return membershipRepository.findByRoomId(roomId).asFlow()
    }

    suspend fun getMembershipsByUserId(userId: String): Flow<Membership> {
        return membershipRepository.findByUserId(userId).asFlow()
    }

    suspend fun getMembershipsSizeByUserId(userId: String): Long {
        return membershipRepository.countByUserId(userId).awaitFirst()
    }

    suspend fun getMembershipsSizeByRoomId(roomId: String): Long {
        return membershipRepository.countByRoomId(roomId).awaitFirst()
    }

    suspend fun hasRoomOnlyManagedUsersLeft(roomId: String): Boolean {
        return membershipRepository.containsOnlyManagedMembersByRoomId(roomId).awaitFirst()
    }

    suspend fun deleteMembership(userId: String, roomId: String) {
        membershipRepository.deleteByUserIdAndRoomId(userId, roomId).awaitFirstOrNull()
    }

    suspend fun containsMembersByRoomId(roomId: String, members: Set<String>): Boolean {
        return membershipRepository.containsMembersByRoomId(roomId, members).awaitFirst()
    }
}