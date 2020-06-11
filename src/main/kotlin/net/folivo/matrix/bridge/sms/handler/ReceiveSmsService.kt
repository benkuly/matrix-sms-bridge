package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.user.SmsMatrixAppserviceUserService
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class ReceiveSmsService(
        private val matrixClient: MatrixClient,
        private val userService: SmsMatrixAppserviceUserService,
        private val matrixBotProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties
) {

    companion object {
        const val NO_ANSWER = "NO_ANSWER"
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    fun receiveSms(body: String, sender: String): Mono<String> {
        val userIdMono = Mono.fromCallable {
            sender.matches(Regex("\\+[0-9]{6,15}"))
        }.flatMap {
            if (it) {
                Mono.just("@sms_${sender.substringAfter('+')}:${matrixBotProperties.serverName}")
            } else {
                Mono.error(
                        MatrixServerException(
                                BAD_REQUEST,
                                ErrorResponse(
                                        "NET.FOLIVO_BAD_REQUEST",
                                        "The sender did not match our regex for international telephone numbers."
                                )
                        )
                )
            }
        }

        val mappingToken =
                Regex("#[0-9]{1,9}").find(body)
                        ?.value?.substringAfter('#')?.toInt()
        return userIdMono
                .flatMap { userId ->
                    userService.getRoomId(
                            userId = userId,
                            mappingToken = mappingToken
                    ).zipWith(Mono.just(userId))
                }.flatMap {
                    val roomId = it.t1
                    val userId = it.t2
                    LOG.debug("receive SMS from $sender to $roomId")
                    matrixClient.roomsApi.sendRoomEvent(roomId, TextMessageEventContent(body), asUserId = userId)
                            .doOnError { LOG.error("could not send SMS message to room $roomId as user $userId") }
                }.flatMap { Mono.just(NO_ANSWER) }
                .switchIfEmpty(
                        Mono.fromCallable<String> {
                            smsBridgeProperties.defaultRoomId
                        }.flatMap { defaultRoomId ->
                            LOG.debug("receive SMS without or wrong mappingToken from $sender to default room $defaultRoomId")
                            val message = smsBridgeProperties.templates.defaultRoomIncomingMessage
                                    .replace("{sender}", sender)
                                    .replace("{body}", body)
                            matrixClient.roomsApi.sendRoomEvent(defaultRoomId, TextMessageEventContent(message))
                                    .doOnError { LOG.error("could not send SMS message to default room $defaultRoomId as user appservice user") }
                        }.map {
                            smsBridgeProperties.templates.answerInvalidTokenWithDefaultRoom ?: NO_ANSWER
                        }.switchIfEmpty(
                                Mono.just(
                                        smsBridgeProperties.templates.answerInvalidTokenWithoutDefaultRoom
                                        ?: NO_ANSWER
                                )
                        )
                )
                .filter { it != NO_ANSWER && it.isNotEmpty() }
    }
}