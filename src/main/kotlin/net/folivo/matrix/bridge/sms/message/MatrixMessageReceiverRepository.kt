package net.folivo.matrix.bridge.sms.message

import kotlinx.coroutines.flow.Flow
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface MatrixMessageReceiverRepository : CoroutineCrudRepository<MatrixMessageReceiver, Long> {

    fun findByRoomMessageId(roomMessageId: Long): Flow<MatrixMessageReceiver>
}