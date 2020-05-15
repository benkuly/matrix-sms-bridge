package net.folivo.matrix.bridge.sms.mapping

import org.neo4j.springframework.data.repository.config.ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME
import org.neo4j.springframework.data.repository.query.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono

@Repository
interface SmsRoomRepository : ReactiveCrudRepository<SmsRoom, Long> {

    @Query("MATCH (s:SmsRoom) - [:OWNED_BY] -> (:AppserviceUser {userId:\$userId}) WITH max(s.mappingToken) as maxi WHERE NOT maxi IS NULL RETURN maxi")
    fun findLastMappingTokenByUserId(userId: String): Mono<Int>

    @Transactional(DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    fun findByBridgedRoomRoomIdAndUserUserId(roomId: String, userId: String): Mono<SmsRoom>

    @Transactional(DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    fun findByMappingTokenAndUserUserId(mappingToken: Int, userId: String): Mono<SmsRoom>
}