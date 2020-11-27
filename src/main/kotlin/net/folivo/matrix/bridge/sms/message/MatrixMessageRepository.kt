package net.folivo.matrix.bridge.sms.message

import net.folivo.matrix.core.model.MatrixId.RoomId
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface MatrixMessageRepository : CoroutineCrudRepository<MatrixMessage, Long> {
    suspend fun deleteByRoomId(roomId: RoomId)
}