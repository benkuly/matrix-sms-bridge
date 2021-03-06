package net.folivo.matrix.bridge.sms.provider.android

import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface AndroidSmsProcessedRepository : CoroutineCrudRepository<AndroidSmsProcessed, Long> {
}