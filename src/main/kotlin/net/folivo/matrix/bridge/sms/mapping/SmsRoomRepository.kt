package net.folivo.matrix.bridge.sms.mapping

import org.neo4j.springframework.data.repository.query.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface SmsRoomRepository : ReactiveCrudRepository<SmsRoom, String> {

    @Query("MATCH (s:SmsRoom) - [:OWNED_BY] -> MATCH (u {userId:\$userId}) RETURN max(s.mappingToken)")
    fun findLastMappingTokenByUserId(userId: String): Mono<Int>

    @Query("MATCH (u {userId:\$userId}) <- [:OWNED_BY] - (s:SmsRoom) - [:BRIDGED_TO] -> (r {roomId:\$roomId}) RETURN s")
    fun findByRoomIdAndUserId(roomId: String, userId: String): Mono<SmsRoom>

    @Query("MATCH (s:SmsRoom {mappingToken:\$mappingToken}) - [:OWNED_BY] -> MATCH (u {userId:\$userId}) RETURN s")
    fun findByMappingTokenAndUserId(mappingToken: Int, userId: String): Mono<SmsRoom>
}