package net.folivo.matrix.bridge.sms.message

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface MatrixRoomMessageRepository : CoroutineCrudRepository<MatrixRoomMessage, UUID> {
}