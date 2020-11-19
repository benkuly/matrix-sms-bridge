package net.folivo.matrix.bridge.sms.provider.android

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.ReceiveSmsService
import net.folivo.matrix.bridge.sms.provider.PhoneNumberService
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

class AndroidSmsProvider(
        private val receiveSmsService: ReceiveSmsService,
        private val phoneNumberService: PhoneNumberService,
        private val processedRepository: AndroidSmsProcessedRepository,
        private val outSmsMessageRepository: AndroidOutSmsMessageRepository,
        private val webClient: WebClient,
        private val matrixClient: MatrixClient,
        private val smsBridgeProperties: SmsBridgeProperties
) : SmsProvider {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override suspend fun sendSms(receiver: String, body: String) {
        try {
            sendOutSmsMessageRequest(AndroidOutSmsMessageRequest(receiver, body))
        } catch (error: Throwable) {
            if (error is AndroidSmsProviderException && error.status == BAD_REQUEST) {
                throw error
            } else {
                LOG.error("could not send sms message to android sms gateway: ${error.message}")
                outSmsMessageRepository.save(AndroidOutSmsMessage(receiver, body))
                if (smsBridgeProperties.defaultRoomId != null && outSmsMessageRepository.count() == 1L) {
                    matrixClient.roomsApi.sendRoomEvent(
                            smsBridgeProperties.defaultRoomId,
                            NoticeMessageEventContent(
                                    smsBridgeProperties.templates.providerSendError.replace(
                                            "{error}", error.message ?: "unknown"
                                    )
                            )
                    )
                }
            }
        }
    }

    suspend fun sendOutFailedMessages() {
        if (outSmsMessageRepository.count() > 0L) {
            outSmsMessageRepository.findAll().collect {
                sendOutSmsMessageRequest(AndroidOutSmsMessageRequest(it.receiver, it.body))
            }
            if (smsBridgeProperties.defaultRoomId != null) {
                matrixClient.roomsApi.sendRoomEvent(
                        smsBridgeProperties.defaultRoomId,
                        NoticeMessageEventContent(smsBridgeProperties.templates.providerResendSuccess)
                )
            }
        }
    }

    private suspend fun sendOutSmsMessageRequest(message: AndroidOutSmsMessageRequest) {
        webClient.post().uri("/messages/out").bodyValue(message)
                .retrieve().toBodilessEntity().awaitFirstOrNull()
    }

    suspend fun getAndProcessNewMessages() {
        LOG.debug("request new messages")
        val lastProcessed = processedRepository.findById(1)
        val response = webClient.get().uri {
            it.apply {
                path("/messages/in")
                if (lastProcessed != null) queryParam("after", lastProcessed.lastProcessedId)
            }.build()
        }.retrieve().awaitBody<AndroidInSmsMessagesResponse>()
        response.messages
                .sortedBy { it.id }
                .fold(lastProcessed, { process, message ->
                    receiveSmsService.receiveSms(
                            message.body,
                            phoneNumberService.parseToInternationalNumber(message.sender)
                    )
                    processedRepository.save(
                            process?.copy(lastProcessedId = message.id)
                            ?: AndroidSmsProcessed(1, message.id)
                    )
                })
        LOG.debug("processed new messages")
    }


}