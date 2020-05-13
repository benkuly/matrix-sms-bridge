package net.folivo.matrix.bridge.sms.mapping

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class ReceiveSmsService(
        private val matrixClient: MatrixClient,
        private val smsRoomRepository: SmsRoomRepository,
        private val matrixBotProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties
) {

    private val logger = LoggerFactory.getLogger(ReceiveSmsService::class.java)

    fun receiveSms(body: String, sender: String): Mono<String> {
        val userIdMono = Mono.fromCallable { "@sms_$sender:${matrixBotProperties.serverName}" }
        val mappingTokenMono = Mono.fromCallable<Int> {
            Regex("#[0-9]{1,9}").find(body)
                    ?.value?.substringAfter('#')?.toInt()
        }
        return Mono.zip(userIdMono, mappingTokenMono)
                .flatMap {
                    smsRoomRepository.findByMappingTokenAndUserId(userId = it.t1, mappingToken = it.t2)
                }.flatMap {
                    val roomId = it.bridgedRoom.roomId
                    val userId = it.user.userId
                    logger.debug("receive SMS from $sender to $roomId")
                    matrixClient.roomsApi.sendRoomEvent(roomId, TextMessageEventContent(body), asUserId = userId)
                            .doOnError { logger.error("could not send SMS message to room $roomId as user $userId") }
                            .flatMap { Mono.empty<String>() }
                }.switchIfEmpty(
                        Mono.fromCallable<String> {
                            smsBridgeProperties.defaultRoomId
                        }.flatMap { defaultRoomId ->
                            logger.debug("receive SMS without or wrong mappingToken from $sender to default room $defaultRoomId")
                            matrixClient.roomsApi.sendRoomEvent(defaultRoomId, TextMessageEventContent(body))
                                    .doOnError { logger.error("could not send SMS message to default room $defaultRoomId as user appservice user") }
                        }.flatMap {
                            Mono.just(smsBridgeProperties.templates.wrongToken)
                        }
                )
    }
}