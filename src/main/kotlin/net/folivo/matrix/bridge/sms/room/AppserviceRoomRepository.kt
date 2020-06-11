package net.folivo.matrix.bridge.sms.room

import org.neo4j.springframework.data.repository.query.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux

@Repository
interface AppserviceRoomRepository : ReactiveCrudRepository<AppserviceRoom, String> {
    
    @Transactional
    @Query(
            "MATCH (user:AppserviceUser)-[:MEMBER_OF]->(room:AppserviceRoom) " +
            "WHERE user.userId in \$members " +
            "WITH room, size(\$members) as inputCnt, count(DISTINCT user) as cnt " +
            "WHERE cnt = inputCnt " +
            "RETURN room"
    )// TODO fix query to load also users and therefore allow check to find real matching room (without other managed users) in SendSmsCommandHelper
    // TODO or maybe write a query, which does that
    fun findByMembersUserIdContaining(members: Set<String>): Flux<AppserviceRoom>
}