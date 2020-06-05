package net.folivo.matrix.bridge.sms.room

import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux

@Repository
interface AppserviceRoomRepository : ReactiveCrudRepository<AppserviceRoom, String> {
    fun findByMembersKeyUserIdContaining(members: List<String>): Flux<AppserviceRoom>
}