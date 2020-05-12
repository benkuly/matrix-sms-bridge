package net.folivo.matrix.bot.appservice.room

import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface SmsRoomRepository : ReactiveCrudRepository<SmsRoom, String> {
    fun findByMappingToken(mappingToken: String): Mono<SmsRoom>
    fun findByBridgedRoom(bridgedRoom: AppserviceRoom): Mono<SmsRoom>
}