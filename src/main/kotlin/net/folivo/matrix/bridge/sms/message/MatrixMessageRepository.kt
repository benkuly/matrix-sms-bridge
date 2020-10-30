package net.folivo.matrix.bridge.sms.message

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface MatrixMessageRepository : CoroutineCrudRepository<MatrixMessage, Long> {
}