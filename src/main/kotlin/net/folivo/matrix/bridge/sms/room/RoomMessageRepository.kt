package net.folivo.matrix.bridge.sms.room

import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux

@Repository
interface RoomMessageRepository : ReactiveCrudRepository<RoomMessage, Long> {
    fun findByRoomId(roomId: String): Flux<RoomMessage>
}