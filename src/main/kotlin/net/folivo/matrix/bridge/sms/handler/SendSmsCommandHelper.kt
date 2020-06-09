package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.ALWAYS
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.AUTO
import net.folivo.matrix.bridge.sms.room.AppserviceRoomRepository
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.neo4j.springframework.data.repository.config.ReactiveNeo4jRepositoryConfigurationExtension
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

@Component
class SendSmsCommandHelper(
        private val roomRepository: AppserviceRoomRepository,
        private val matrixClient: MatrixClient,
        private val botProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties
) {
    enum class RoomCreationMode {
        AUTO, ALWAYS, NO
    }

    // FIXME test
    @Transactional(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
    fun createRoomAndSendMessage(
            body: String,
            sender: String,
            receiverNumbers: List<String>,
            roomName: String?,
            roomCreationMode: RoomCreationMode
    ): Mono<String> {
        val receiverIds = receiverNumbers.map { "@sms_$it:${botProperties.serverName}" }
        val members = listOf(sender, *receiverIds.toTypedArray())
        return roomRepository.findByMembersUserIdContaining(members)
                .limitRequest(2)
                .collectList()
                .flatMap { rooms ->
                    if (rooms.size == 0 && roomCreationMode == AUTO || roomCreationMode == ALWAYS) {
                        matrixClient.roomsApi.createRoom(name = roomName, invite = members)
                                .delayUntil { roomId ->
                                    matrixClient.roomsApi.getJoinedMembers(roomId)
                                            .flatMap {
                                                if (it.joined.keys.containsAll(receiverIds)) {
                                                    Mono.just(roomId)
                                                } else {
                                                    Mono.error(
                                                            MatrixServerException(
                                                                    NOT_FOUND,
                                                                    ErrorResponse(
                                                                            "NET_FOLIVO.NOT_FOUND",
                                                                            "Some of receivers didn't join the room $roomId."
                                                                    )
                                                            )
                                                    )
                                                }
                                            }.retryWhen(Retry.backoff(5, Duration.ofMillis(500)))
                                }
                                .flatMap { roomId ->
                                    matrixClient.roomsApi.sendRoomEvent(
                                            roomId = roomId,
                                            eventContent = TextMessageEventContent(
                                                    smsBridgeProperties.templates.botSmsSendNewRoomMessage
                                                            .replace("{sender}", sender)
                                                            .replace("{body}", body)
                                            )
                                    )
                                }.map { smsBridgeProperties.templates.botSmsSendCreatedRoomAndSendMessage }
                    } else if (rooms.size == 1) {
                        matrixClient.roomsApi.sendRoomEvent(
                                roomId = rooms[0].roomId,
                                eventContent = TextMessageEventContent(
                                        smsBridgeProperties.templates.defaultRoomIncomingMessage
                                                .replace("{sender}", sender)
                                                .replace("{body}", body)
                                )
                        ).map { smsBridgeProperties.templates.botSmsSendSendMessage }
                    } else if (rooms.size > 1) {
                        Mono.just(smsBridgeProperties.templates.botSmsSendTooManyRooms)
                    } else {
                        Mono.just(smsBridgeProperties.templates.botSmsSendNoSendMessage)
                    }
                }.map { it.replace("{receiverNumbers}", receiverNumbers.joinToString()) }
    }
}