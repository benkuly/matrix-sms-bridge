package net.folivo.matrix.sms.bridge.mapping

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import net.folivo.matrix.sms.bridge.SmsBridgeProperties
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

    fun receiveSms(body: String, sender: String): Mono<Void> {
        val userId = Mono.fromCallable { "@sms_$sender:${matrixBotProperties.serverName}" }
        val mappingToken = Mono.fromCallable<Int> {
            Regex("#[0-9]{1,9}").find(body)
                    ?.value?.substringAfter('#')?.toInt()
        }
        return Mono.zip(userId, mappingToken)
                .flatMap {
                    smsRoomRepository.findByMappingTokenAndUserId(userId = it.t1, mappingToken = it.t2)
                }.flatMap {
                    val roomId = it.bridgedRoom.roomId
                    logger.debug("receive SMS from $sender to $roomId")
                    matrixClient.roomsApi.sendRoomEvent(roomId, TextMessageEventContent(body))
                }.switchIfEmpty(
                        Mono.fromCallable<String> {
                            smsBridgeProperties.defaultRoomId
                        }.flatMap {
                            matrixClient.roomsApi.sendRoomEvent(it, TextMessageEventContent(body)).doFirst {
                                logger.debug("receive SMS without mappingToken from $sender to default room $it")
                            }
                        }
                ).then()
    }
}