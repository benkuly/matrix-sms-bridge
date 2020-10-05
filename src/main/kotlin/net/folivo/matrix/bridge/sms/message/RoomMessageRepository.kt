package net.folivo.matrix.bridge.sms.message

import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RoomMessageRepository : ReactiveCrudRepository<RoomMessage, UUID> {
}