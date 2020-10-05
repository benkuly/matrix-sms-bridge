package net.folivo.matrix.bridge.sms.membership

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface MembershipRepository : ReactiveCrudRepository<Membership, String> {

    fun findByUserIdSortByMappingTokenDesc(userId: String): Flux<Membership>

    fun findByRoomId(roomId: String): Flux<Membership>

    fun findByUserId(userId: String): Flux<Membership>

    fun countByRoomId(roomId: String): Flux<Long>

    fun countByUserId(userId: String): Flux<Long>

    fun findByUserIdAndRoomId(userId: String, roomId: String): Mono<Membership>

    fun deleteByUserIdAndRoomId(userId: String, roomId: String): Mono<Void>

    @Query(
            """
            SELECT CASE WHEN COUNT(*) = :#{#members.size} THEN 'true' ELSE 'false' END 
            FROM Membership m 
            WHERE m.fk_Membership_AppserviceRoom = :roomId AND m.fk_Membership_AppserviceUser IN :members
            """
    )
    fun containsMembersByRoomId(roomId: String, members: Set<String>): Mono<Boolean>

    @Query(
            """
            SELECT CASE WHEN COUNT(*) = 0 THEN 'true' ELSE 'false' END 
            FROM Membership m 
            JOIN AppserviceUser u ON m.fk_Membership_AppserviceUser = u.id 
            WHERE m.fk_Membership_AppserviceRoom = :roomId AND u.isManaged = false
            """
    )
    fun containsOnlyManagedMembersByRoomId(roomId: String): Mono<Boolean>

}