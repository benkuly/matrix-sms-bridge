package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.appservice.api.AppserviceHandlerHelper
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.ALWAYS
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.AUTO
import net.folivo.matrix.bridge.sms.room.AppserviceRoomRepository
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class SendSmsCommandHelper(
        private val roomRepository: AppserviceRoomRepository,
        private val helper: AppserviceHandlerHelper,
        private val matrixClient: MatrixClient,
        private val botProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    enum class RoomCreationMode {
        AUTO, ALWAYS, NO
    }

    fun createRoomAndSendMessage(
            body: String,
            sender: String,
            receiverNumbers: List<String>,
            roomName: String?,
            roomCreationMode: RoomCreationMode
    ): Mono<String> {
        val receiverIds = receiverNumbers.map { "@sms_${it.removePrefix("+")}:${botProperties.serverName}" }
        val members = listOf(sender, *receiverIds.toTypedArray())
        return roomRepository.findByMembersUserIdContaining(members)
                .limitRequest(2)
                .collectList()
                .flatMap { rooms ->
                    if (rooms.size == 0 && roomCreationMode == AUTO || roomCreationMode == ALWAYS) {
                        LOG.debug("create room and send message")
                        matrixClient.roomsApi.createRoom(name = roomName, invite = members)
                                .flatMap { roomId ->
                                    Flux.fromIterable(receiverIds)
                                            .flatMap { receiverId ->
                                                matrixClient.roomsApi.joinRoom(
                                                        roomIdOrAlias = roomId,
                                                        asUserId = receiverId
                                                ).onErrorResume { error ->
                                                    registerOnMatrixException(receiverId, error)
                                                            .then(Mono.just(true)) // TODO scary workaround
                                                            .flatMap {
                                                                matrixClient.roomsApi.joinRoom(
                                                                        roomIdOrAlias = roomId,
                                                                        asUserId = receiverId
                                                                )
                                                            }
                                                }.then()
                                            }.then(
                                                    matrixClient.roomsApi.sendRoomEvent(
                                                            roomId = roomId,
                                                            eventContent = TextMessageEventContent(
                                                                    smsBridgeProperties.templates.botSmsSendNewRoomMessage
                                                                            .replace("{sender}", sender)
                                                                            .replace("{body}", body)
                                                            )
                                                    )
                                            )
                                }.map { smsBridgeProperties.templates.botSmsSendCreatedRoomAndSendMessage }
                    } else if (rooms.size == 1) {
                        LOG.debug("only send message")
                        matrixClient.roomsApi.sendRoomEvent(
                                roomId = rooms[0].roomId,
                                eventContent = TextMessageEventContent(
                                        smsBridgeProperties.templates.botSmsSendNewRoomMessage
                                                .replace("{sender}", sender)
                                                .replace("{body}", body)
                                )
                        ).map { smsBridgeProperties.templates.botSmsSendSendMessage }
                    } else if (rooms.size > 1) {
                        Mono.just(smsBridgeProperties.templates.botSmsSendTooManyRooms)
                    } else {
                        Mono.just(smsBridgeProperties.templates.botSmsSendDisabledRoomCreation)
                    }
                }.map { it.replace("{receiverNumbers}", receiverNumbers.joinToString()) }
                .onErrorResume {
                    LOG.warn("trying to create room, join room or send message failed", it)
                    Mono.just(
                            smsBridgeProperties.templates.botSmsSendError
                                    .replace("{error}", it.message ?: "unknown")
                                    .replace("{receiverNumbers}", receiverNumbers.joinToString())
                    )
                }
    }

    //FIXME test
    private fun registerOnMatrixException(userId: String, error: Throwable): Mono<Void> {
        return if (error is MatrixServerException && error.statusCode == FORBIDDEN) {
            LOG.warn("try to register user because of ${error.errorResponse}")
            helper.registerAndSaveUser(userId).then()
        } else Mono.error(error)
    }
}