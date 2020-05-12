package net.folivo.matrix.sms.bridge.mapping

import org.neo4j.springframework.data.repository.query.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface SmsRoomRepository : ReactiveCrudRepository<SmsRoom, String> {

    @Query("MATCH (s:SmsRoom) <- [:OWNED_BY] - MATCH (u {userId:\$userId}) RETURN max(s.mappingToken)")
    fun findLastMappingTokenByUserId(userId: String): Mono<Int>

    @Query("MATCH (u {userId:\$userId}) <- [:OWNED_BY] - (s:SmsRoom) - [:BRIDGED_TO] -> (r {roomId:\$roomId})")
    fun findByRoomIdAndUserId(roomId: String, userId: String): Mono<SmsRoom>

    @Query("MATCH (s:SmsRoom) - [:BRIDGED_TO] -> (r {roomId:\$roomId})")
    fun findByRoomId(roomId: String): Flux<SmsRoom>
}