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

class AndroidSmsProvider(
        private val receiveSmsService: ReceiveSmsService,
        private val phoneNumberService: PhoneNumberService,
        private val processedRepository: AndroidSmsProcessedRepository,
        private val webClient: WebClient
) : SmsProvider {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override suspend fun sendSms(receiver: String, body: String) {
        webClient.post().uri("/messages/out")
                .bodyValue(
                        AndroidSmsMessagesRequest(receiver, body)
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
        val lastProcessed = processedRepository.findById(1)
        val response = webClient.get().uri {
            it.apply {
                path("/messages/in")
                if (lastProcessed != null) queryParam("after", lastProcessed.lastProcessedId)
            }.build()
        }.retrieve().awaitBody<AndroidSmsMessagesResponse>()
        response.messages
                .sortedBy { it.id }
                .fold(lastProcessed, { lastProcessed, message ->
                    receiveSmsService.receiveSms(
                            message.body,
                            phoneNumberService.parseToInternationalNumber(message.sender)
                    )
                    processedRepository.save(
                            lastProcessed?.copy(lastProcessedId = message.id)
                            ?: AndroidSmsProcessed(1, message.id)
                    )
                })
        LOG.debug("processed new messages")
    }

    private fun logAttempt(): RetryPolicy<Throwable> {
        return {
            LOG.error("could not retrieve messages from android device: ${reason.message}")
            ContinueRetrying
        }
    }
}