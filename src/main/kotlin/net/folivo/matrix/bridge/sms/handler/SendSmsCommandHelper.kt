package net.folivo.matrix.bridge.sms.handler

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.ALWAYS
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.AUTO
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.room.RoomMessage
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import net.folivo.matrix.restclient.api.rooms.Preset.TRUSTED_PRIVATE
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit


@Component
class SendSmsCommandHelper(
        private val roomService: SmsMatrixAppserviceRoomService,
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

    //FIXME test
    suspend fun createRoomAndSendMessage(
            body: String?,
            sender: String,
            receiverNumbers: List<String>,
            roomName: String?,
            sendAfterLocal: LocalDateTime?,
            roomCreationMode: RoomCreationMode
    ): String {
        val receiverIds = receiverNumbers.map { "@sms_${it.removePrefix("+")}:${botProperties.serverName}" }
        val membersWithoutBot = setOf(sender, *receiverIds.toTypedArray())

        val rooms = roomService.getRoomsWithUsers(membersWithoutBot)
                .take(2)
                .toList()

        val sendAfter = sendAfterLocal?.atZone(ZoneId.of(smsBridgeProperties.defaultTimeZone))?.toInstant()
        try {
            val answer = if (rooms.isEmpty() && roomCreationMode == AUTO || roomCreationMode == ALWAYS) {
                LOG.debug("create room and send message")
                val createdRoomId = matrixClient.roomsApi.createRoom(
                        name = roomName,
                        invite = membersWithoutBot,
                        preset = TRUSTED_PRIVATE
                )

                if (!body.isNullOrBlank()) {
                    val room = roomService.getOrCreateRoom(createdRoomId)
                    sendMessageLater(room, sender, body, receiverIds, sendAfter, sendAfterLocal)
                }
                smsBridgeProperties.templates.botSmsSendCreatedRoomAndSendMessage
            } else if (rooms.size == 1) {
                LOG.debug("only send message")
                val room = roomService.getOrCreateRoom(rooms[0].roomId)
                if (body.isNullOrBlank()) {
                    smsBridgeProperties.templates.botSmsSendNoMessage
                } else {
                    val botIsMember = room.members.keys.find { it.userId == "@${botProperties.username}:${botProperties.serverName}" } != null
                    val expectedManagedMemberSize = if (botIsMember) receiverIds.size + 1 else receiverIds.size
                    val membersMatch = room.members.keys.count { it.isManaged } == expectedManagedMemberSize
                    if (membersMatch) {
                        LOG.debug("send message to room ${room.roomId}")
                        if (botIsMember) {
                            if (sendAfter == null) {
                                matrixClient.roomsApi.sendRoomEvent(
                                        roomId = rooms[0].roomId,
                                        eventContent = TextMessageEventContent(
                                                smsBridgeProperties.templates.botSmsSendNewRoomMessage
                                                        .replace("{sender}", sender)
                                                        .replace("{body}", body)
                                        )
                                )
                            } else {
                                sendMessageLater(room, sender, body, receiverIds, sendAfter, sendAfterLocal, true)
                            }
                        } else {
                            LOG.debug("try to invite sms bot user to room ${room.roomId} and send message later")
                            matrixClient.roomsApi.inviteUser(
                                    roomId = room.roomId,
                                    userId = "@${botProperties.username}:${botProperties.serverName}",
                                    asUserId = receiverIds.first()
                            )
                            sendMessageLater(room, sender, body, receiverIds, sendAfter, sendAfterLocal)
                        }
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

    private suspend fun sendMessageLater(
            room: AppserviceRoom,
            sender: String,
            body: String,
            receiverIds: List<String>,
            sendAfter: Instant?,
            sendAfterLocal: LocalDateTime?,
            notifyDirectly: Boolean = false
    ) {
        roomService.sendMessageLater(
                RoomMessage(
                        room = room,
                        body = smsBridgeProperties.templates.botSmsSendNewRoomMessage
                                .replace("{sender}", sender)
                                .replace("{body}", body),
                        requiredReceiverIds = receiverIds.toSet(),
                        sendAfter = sendAfter ?: Instant.now()
                )
        )

        if (sendAfter != null && Instant.now().until(sendAfter, ChronoUnit.SECONDS) > 15) {
            if (notifyDirectly) {
                matrixClient.roomsApi.sendRoomEvent(
                        roomId = room.roomId,
                        eventContent = NoticeMessageEventContent(
                                smsBridgeProperties.templates.botSmsSendNoticeDelayedMessage
                                        .replace("{sendAfter}", sendAfterLocal.toString())
                        )
                )
            } else {
                roomService.sendMessageLater(
                        RoomMessage(
                                room = room,
                                body = smsBridgeProperties.templates.botSmsSendNoticeDelayedMessage
                                        .replace("{sendAfter}", sendAfterLocal.toString()),
                                requiredReceiverIds = receiverIds.toSet(),
                                isNotice = true
                        )
                )
            }
        }
    }
}