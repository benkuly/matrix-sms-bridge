package net.folivo.matrix.bridge.sms.room

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface AppserviceRoomRepository : ReactiveCrudRepository<AppserviceRoom, String> { //FIXME test queries

    @Query(
            """
            WITH countedRooms AS (SELECT COUNT(*) AS memberSize,m.fk_Membership_AppserviceRoom FROM Membership m 
            WHERE m.fk_Membership_AppserviceUser IN :members 
            GROUP_BY m.fk_Membership_AppserviceRoom) 
            SELECT * FROM AppserviceRoom room 
            JOIN countedRooms ON countedRooms.fk_Membership_AppserviceRoom = room.id 
            WHERE countedRooms.memberSize = :#{#members.size}
            """
    )
    fun findByContainingMembers(members: Set<String>): Flux<AppserviceRoom>

    @Query(
            """
            SELECT * FROM AppserviceRoom r 
            JOIN Membership m ON m.k_Membership_AppserviceRoom = r.id 
            WHERE m.fk_Membership_AppserviceUser = :userId AND m.mappingToken = :mappingToken
            """
    )
    fun findByMemberAndMappingToken(userId: String, mappingToken: Int): Mono<AppserviceRoom>

    @Query(
            """
            SELECT * FROM AppserviceRoom r 
            JOIN Membership m ON m.k_Membership_AppserviceRoom = r.id 
            WHERE m.fk_Membership_AppserviceUser = :userId
            """
    )
    fun findByMember(userId: String): Flux<AppserviceRoom>
}