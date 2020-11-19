package net.folivo.matrix.bridge.sms.provider.android

import com.github.michaelbull.retry.ContinueRetrying
import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.policy.binaryExponentialBackoff
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener

class AndroidSmsProviderLauncher(private val androidSmsProvider: AndroidSmsProvider) {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    @EventListener(ApplicationReadyEvent::class)
    fun startLoops() {
        GlobalScope.launch {
            while (true) {
                retry(binaryExponentialBackoff(base = 5000, max = 60000) + logReceiveAttempt()) {
                    androidSmsProvider.getAndProcessNewMessages()
                    delay(5000)
                }
            }
        }
        GlobalScope.launch {
            while (true) {
                retry(binaryExponentialBackoff(base = 10000, max = 120000) + logSendAttempt()) {
                    androidSmsProvider.sendOutFailedMessages()
                    delay(10000)
                }
            }
        }
    }

    private fun logReceiveAttempt(): RetryPolicy<Throwable> {
        return {
            LOG.error("could not retrieve messages from android device or process them: ${reason.message}")
            ContinueRetrying
        }
    }

    private fun logSendAttempt(): RetryPolicy<Throwable> {
        return {
            LOG.error("could not send messages to android device: ${reason.message}")
            ContinueRetrying
        }
    }
}