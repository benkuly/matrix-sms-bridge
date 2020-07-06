package net.folivo.matrix.bridge.sms.handler

import kotlinx.coroutines.reactive.awaitFirst
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

    suspend fun createRoomAndSendMessage(
            body: String?,
            sender: String,
            receiverNumbers: List<String>,
            roomName: String?,
            roomCreationMode: RoomCreationMode
    ): String {
        val receiverIds = receiverNumbers.map { "@sms_${it.removePrefix("+")}:${botProperties.serverName}" }
        val membersWithoutBot = setOf(sender, *receiverIds.toTypedArray())
        val members = setOf(*membersWithoutBot.toTypedArray())

        val rooms = roomRepository.findByMembersUserIdContaining(members)
                .limitRequest(2)
                .collectList()
                .awaitFirst()

        try {
            val answer = if (rooms.size == 0 && roomCreationMode == AUTO || roomCreationMode == ALWAYS) {
                LOG.debug("create room and send message")
                //FIXME register all members that does not exist in db?
                val createdRoomId = matrixClient.roomsApi.createRoom(
                        name = roomName,
                        invite = membersWithoutBot,
                        preset = TRUSTED_PRIVATE
                )

                receiverIds.forEach { receiverId ->
                    try {
                        matrixClient.roomsApi.joinRoom(// FIXME because of autojoin one of the both will always throw an exception
                                // FIXME maybe add version when fixed
                                roomIdOrAlias = createdRoomId,
                                asUserId = receiverId
                        )
                    } catch (error: Throwable) {//FIXME remove when registered before
                        registerOnMatrixException(receiverId, error)

                        matrixClient.roomsApi.joinRoom(
                                roomIdOrAlias = createdRoomId,
                                asUserId = receiverId
                        )
                    }
                }
                if (!body.isNullOrBlank()) matrixClient.roomsApi.sendRoomEvent(
                        roomId = createdRoomId,
                        eventContent = TextMessageEventContent(
                                smsBridgeProperties.templates.botSmsSendNewRoomMessage
                                        .replace("{sender}", sender)
                                        .replace("{body}", body)
                        )
                )
                smsBridgeProperties.templates.botSmsSendCreatedRoomAndSendMessage
            } else if (rooms.size == 1) {
                LOG.debug("only send message")
                val room = roomRepository.findById(rooms[0].roomId).awaitFirst()
                if (body.isNullOrBlank()) {
                    smsBridgeProperties.templates.botSmsSendNoMessage
                } else {
                    val botIsMember = room.members.keys.find { it.userId == "@${botProperties.username}:${botProperties.serverName}" } != null
                    val expectedManagedMemberSize = if (botIsMember) receiverIds.size + 1 else receiverIds.size
                    val membersMatch = room.members.keys.count { it.isManaged } == expectedManagedMemberSize
                    if (membersMatch) {
                        if (!botIsMember) {
                            LOG.debug("try to invite sms bot user to room ${room.roomId}")
                            matrixClient.roomsApi.inviteUser(
                                    roomId = room.roomId,
                                    userId = "@${botProperties.username}:${botProperties.serverName}",
                                    asUserId = receiverIds.first()
                            )
                        }
                        LOG.debug("send message to room ${room.roomId}")
                        matrixClient.roomsApi.sendRoomEvent(
                                roomId = rooms[0].roomId,
                                eventContent = TextMessageEventContent(
                                        smsBridgeProperties.templates.botSmsSendNewRoomMessage
                                                .replace("{sender}", sender)
                                                .replace("{body}", body)
                                )
                        )
                        smsBridgeProperties.templates.botSmsSendSendMessage
                    } else {
                        smsBridgeProperties.templates.botSmsSendDisabledRoomCreation
                    }
                }
            } else if (rooms.size > 1) {
                smsBridgeProperties.templates.botSmsSendTooManyRooms
            } else {
                smsBridgeProperties.templates.botSmsSendDisabledRoomCreation
            }

            return answer.replace("{receiverNumbers}", receiverNumbers.joinToString())
        } catch (error: Throwable) {
            LOG.warn("trying to create room, join room or send message failed: ${error.message}")
            return smsBridgeProperties.templates.botSmsSendError
                    .replace("{error}", error.message ?: "unknown")
                    .replace("{receiverNumbers}", receiverNumbers.joinToString())
        }
    }

    private suspend fun registerOnMatrixException(userId: String, error: Throwable) {
        if (error is MatrixServerException && error.statusCode == FORBIDDEN) {
            LOG.warn("try to register user because of ${error.errorResponse}")
            helper.registerAndSaveUser(userId)
        }
    }
}