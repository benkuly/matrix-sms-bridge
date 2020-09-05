package net.folivo.matrix.bridge.sms.provider.android

import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface AndroidSmsBatchRepository : ReactiveCrudRepository<AndroidSmsBatch, Long> {
}