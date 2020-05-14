package net.folivo.matrix.bridge.sms.mapping

import org.neo4j.springframework.data.repository.query.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface SmsRoomRepository : ReactiveCrudRepository<SmsRoom, Long> {

    @Query("MATCH (s:SmsRoom) - [:OWNED_BY] -> (:AppserviceUser {userId:\$userId}) WITH max(s.mappingToken) as maxi WHERE NOT maxi IS NULL RETURN maxi")
    fun findLastMappingTokenByUserId(userId: String): Mono<Int>

    @Query("MATCH (:AppserviceUser {userId:\$userId}) <- [:OWNED_BY] - (s:SmsRoom) - [:BRIDGED_TO] -> (:AppserviceRoom {roomId:\$roomId}) RETURN s")
    fun findByRoomIdAndUserId(roomId: String, userId: String): Mono<SmsRoom>

    @Query("MATCH (s:SmsRoom {mappingToken:\$mappingToken}) - [:OWNED_BY] -> (:AppserviceUser {userId:\$userId}) RETURN s")
    fun findByMappingTokenAndUserId(mappingToken: Int, userId: String): Mono<SmsRoom>
}