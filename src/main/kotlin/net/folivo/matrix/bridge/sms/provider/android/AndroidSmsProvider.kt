package net.folivo.matrix.bridge.sms.provider.android

import com.github.michaelbull.retry.ContinueRetrying
import com.github.michaelbull.retry.policy.RetryPolicy
import com.github.michaelbull.retry.policy.binaryExponentialBackoff
import com.github.michaelbull.retry.policy.plus
import com.github.michaelbull.retry.retry
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.folivo.matrix.bridge.sms.handler.ReceiveSmsService
import net.folivo.matrix.bridge.sms.provider.PhoneNumberService
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import org.slf4j.LoggerFactory
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
                ).retrieve().toBodilessEntity().awaitFirstOrNull()
    }

    suspend fun startNewMessageLookupLoop() {
        while (true) {
            retry(binaryExponentialBackoff(base = 1000, max = 300000) + logAttempt()) {
                getNewMessages()
            }
        }
    }

    suspend fun getNewMessages() {
        LOG.debug("request new messages")
        val nextBatch = batchRepository.findById(1).awaitFirstOrNull()
        val response = webClient.get().uri {
            it.apply {
                path("/messages")
                if (nextBatch != null) queryParam("nextBatch", nextBatch.nextBatch)
            }.build()
        }.retrieve().awaitBody<AndroidSmsMessagesResponse>()
        response.messages.forEach {
            receiveSmsService.receiveSms(it.body, phoneNumberService.parseToInternationalNumber(it.sender))
        }
        batchRepository.save(nextBatch?.copy(nextBatch = response.nextBatch) ?: AndroidSmsBatch(1, response.nextBatch))
                .awaitFirstOrNull()
        LOG.debug("processed new messages")
    }

    private fun logAttempt(): RetryPolicy<Throwable> {
        return {
            LOG.error("could not retrieve messages from android device: ${reason.message}")
            ContinueRetrying
        }
    }
}