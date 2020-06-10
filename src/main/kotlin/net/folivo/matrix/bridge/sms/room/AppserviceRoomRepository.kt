package net.folivo.matrix.bridge.sms.room

import org.neo4j.springframework.data.repository.query.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux

@Repository
interface AppserviceRoomRepository : ReactiveCrudRepository<AppserviceRoom, String> {
    @Query(
            "MATCH (user:AppserviceUser)-[:MEMBER_OF]->(room:AppserviceRoom) " +
            "WHERE user.userId in \$members " +
            "WITH room, size(\$members) as inputCnt, count(DISTINCT user) as cnt " +
            "WHERE cnt = inputCnt " +
            "RETURN room"
    )
    fun findByMembersUserIdContaining(members: Set<String>): Flux<AppserviceRoom>

}