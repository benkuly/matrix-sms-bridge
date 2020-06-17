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
import net.folivo.matrix.restclient.api.rooms.Preset.TRUSTED_PRIVATE
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
            body: String?,
            sender: String,
            receiverNumbers: List<String>,
            roomName: String?,
            roomCreationMode: RoomCreationMode
    ): Mono<String> {
        val receiverIds = receiverNumbers.map { "@sms_${it.removePrefix("+")}:${botProperties.serverName}" }
        val membersWithoutBot = setOf(
                sender,
                *receiverIds.toTypedArray()
        )
        val members = setOf(*membersWithoutBot.toTypedArray())
        return roomRepository.findByMembersUserIdContaining(members)
                .limitRequest(2)
                .collectList()
                .flatMap { rooms ->
                    if (rooms.size == 0 && roomCreationMode == AUTO || roomCreationMode == ALWAYS) {
                        LOG.debug("create room and send message")
                        matrixClient.roomsApi.createRoom(
                                name = roomName,
                                invite = membersWithoutBot,
                                preset = TRUSTED_PRIVATE
                        ).flatMap { roomId ->
                            Flux.fromIterable(receiverIds)
                                    .flatMap { receiverId ->
                                        matrixClient.roomsApi.joinRoom(// FIXME because of autojoin one of the both will always throw an exception
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
                                        }
                                    }.then(
                                            if (!body.isNullOrBlank()) matrixClient.roomsApi.sendRoomEvent(
                                                    roomId = roomId,
                                                    eventContent = TextMessageEventContent(
                                                            smsBridgeProperties.templates.botSmsSendNewRoomMessage
                                                                    .replace("{sender}", sender)
                                                                    .replace("{body}", body)
                                                    )
                                            ) else Mono.just("nothing")
                                    )
                        }.map { smsBridgeProperties.templates.botSmsSendCreatedRoomAndSendMessage }
                    } else if (rooms.size == 1) {
                        LOG.debug("only send message")
                        roomRepository.findById(rooms[0].roomId)
                                .flatMap { room ->
                                    if (body.isNullOrBlank()) {
                                        Mono.just(smsBridgeProperties.templates.botSmsSendNoMessage)
                                    } else {
                                        val botIsMember = room.members.keys.find { it.userId == "@${botProperties.username}:${botProperties.serverName}" } != null
                                        val expectedManagedMemberSize = if (botIsMember) receiverIds.size + 1 else receiverIds.size
                                        val membersMatch = room.members.keys.count { it.isManaged } == expectedManagedMemberSize
                                        if (membersMatch) {
                                            Mono.defer {
                                                if (botIsMember) {
                                                    Mono.just(true)
                                                } else {
                                                    LOG.debug("try to invite sms bot user to room ${room.roomId}")
                                                    matrixClient.roomsApi.inviteUser(
                                                            roomId = room.roomId,
                                                            userId = "@${botProperties.username}:${botProperties.serverName}",
                                                            asUserId = receiverIds.first()
                                                    ).thenReturn(true)
                                                }
                                            }.flatMap {
                                                LOG.debug("send message to room ${room.roomId}")
                                                matrixClient.roomsApi.sendRoomEvent(
                                                        roomId = rooms[0].roomId,
                                                        eventContent = TextMessageEventContent(
                                                                smsBridgeProperties.templates.botSmsSendNewRoomMessage
                                                                        .replace("{sender}", sender)
                                                                        .replace("{body}", body)
                                                        )
                                                ).map { smsBridgeProperties.templates.botSmsSendSendMessage }
                                            }
                                        } else {
                                            Mono.just(smsBridgeProperties.templates.botSmsSendDisabledRoomCreation)
                                        }
                                    }
                                }

                    } else if (rooms.size > 1) {
                        Mono.just(smsBridgeProperties.templates.botSmsSendTooManyRooms)
                    } else {
                        Mono.just(smsBridgeProperties.templates.botSmsSendDisabledRoomCreation)
                    }
                }.map { it.replace("{receiverNumbers}", receiverNumbers.joinToString()) }
                .onErrorResume {
                    LOG.warn("trying to create room, join room or send message failed")
                    Mono.just(
                            smsBridgeProperties.templates.botSmsSendError
                                    .replace("{error}", it.message ?: "unknown")
                                    .replace("{receiverNumbers}", receiverNumbers.joinToString())
                    )
                }
    }

    private fun registerOnMatrixException(userId: String, error: Throwable): Mono<Void> {
        return if (error is MatrixServerException && error.statusCode == FORBIDDEN) {
            LOG.warn("try to register user because of ${error.errorResponse}")
            helper.registerAndSaveUser(userId).then()
        } else Mono.error(error)
    }
}