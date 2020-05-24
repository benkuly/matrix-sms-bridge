package net.folivo.matrix.bridge.sms.user

import org.neo4j.springframework.data.repository.query.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface AppserviceUserRepository : ReactiveCrudRepository<AppserviceUser, String> {

    @Query("MATCH (:AppserviceRoom) <- [m:MEMBER:OF] - (:AppserviceUser {userId:\$userId}) WITH max(m.mappingToken) as maxi WHERE NOT maxi IS NULL RETURN maxi")
    fun findLastMappingTokenByUserId(userId: String): Mono<Int>
}