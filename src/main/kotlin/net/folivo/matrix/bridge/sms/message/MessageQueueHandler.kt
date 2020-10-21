package net.folivo.matrix.bridge.sms.message

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!initialsync")
class MessageQueueHandler(private val roomMessageService: MatrixRoomMessageService) : ApplicationListener<ApplicationReadyEvent> {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        GlobalScope.launch {
            while (true) {
                delay(10000)
                try {
                    roomMessageService.processMessageQueue()
                } catch (error: Throwable) {
                    LOG.warn("error while processing messages for deferred sending: ${error.message}")
                }
            }
        }
    }
}