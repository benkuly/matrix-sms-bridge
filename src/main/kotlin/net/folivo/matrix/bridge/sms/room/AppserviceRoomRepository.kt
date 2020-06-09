package net.folivo.matrix.bridge.sms.room

import org.neo4j.springframework.data.repository.query.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux

@Repository
interface AppserviceRoomRepository : ReactiveCrudRepository<AppserviceRoom, String> {
    @Query(
            "MATCH (au:AppserviceUser) " +
            "WHERE au.userId in \$members " +
            "WITH collect(au) as users " +
            "MATCH (ar:AppserviceRoom) " +
            "WHERE ALL(au in users WHERE (au) - [:MEMBER_OF] -> (ar)) " +
            "RETURN ar, collect(users)"
    )
    fun findByMembersUserIdContaining(members: List<String>): Flux<AppserviceRoom>
}