package net.folivo.matrix.bridge.sms.provider.android

import com.github.michaelbull.retry.ContinueRetrying
import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.policy.binaryExponentialBackoff
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.folivo.matrix.bridge.sms.handler.ReceiveSmsService
import net.folivo.matrix.bridge.sms.provider.PhoneNumberService
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

// FIXME test
class AndroidSmsProvider(
        private val receiveSmsService: ReceiveSmsService,
        private val phoneNumberService: PhoneNumberService,
        private val batchRepository: AndroidSmsBatchRepository,
        private val webClient: WebClient
) : SmsProvider {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override suspend fun sendSms(receiver: String, body: String) {
        webClient.post().uri("/messages")
                .bodyValue(
                        AndroidSmsMessage(receiver, body)
                )
    }

    @EventListener(ApplicationReadyEvent::class)
    fun startNewMessageLookupLoop() {
        GlobalScope.launch {
            while (true) {
                retry(binaryExponentialBackoff(base = 1000, max = 300000) + logAttempt()) {
                    val nextBatch: String? = batchRepository.findById(0).awaitFirstOrNull()?.nextBatch
                    val response = webClient.get().uri {
                        it.apply {
                            path("/messages")
                            if (nextBatch != null) queryParam("nextBatch", nextBatch)
                        }.build()
                    }.retrieve().awaitBody<AndroidSmsMessagesResponse>()
                    response.messages.forEach {
                        receiveSmsService.receiveSms(it.body, phoneNumberService.parseToInternationalNumber(it.sender))
                    }
                    batchRepository.save(AndroidSmsBatch(0, response.nextBatch))
                }
            }
        }
    }

    private fun logAttempt(): RetryPolicy<Throwable> {
        return {
            LOG.error("could not retrieve messages from android device: ${reason.message}")
            ContinueRetrying
        }
    }
}