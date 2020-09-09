package net.folivo.matrix.bridge.sms.sync

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class InitialSyncService {

    @EventListener(ApplicationReadyEvent::class)
    fun initialSync() {

    }
}