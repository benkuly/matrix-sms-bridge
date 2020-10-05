package net.folivo.matrix.bridge.sms.handler

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.ALWAYS
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.AUTO
import net.folivo.matrix.bridge.sms.membership.MembershipService
import net.folivo.matrix.bridge.sms.message.RoomMessage
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
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
        private val membershipService: MembershipService,
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
            senderId: String,
            receiverNumbers: List<String>,
            roomName: String?,
            sendAfterLocal: LocalDateTime?,
            roomCreationMode: RoomCreationMode
    ): String {
        val receiverIds = receiverNumbers.map { "@sms_${it.removePrefix("+")}:${botProperties.serverName}" }
        val membersWithoutBot = setOf(senderId, *receiverIds.toTypedArray())

        val rooms = roomService.getRoomsWithMembers(membersWithoutBot)
                .take(2)
                .toList()

        val sendAfter = sendAfterLocal?.atZone(ZoneId.of(smsBridgeProperties.defaultTimeZone))?.toInstant()
        try {
            val answer = if (rooms.isEmpty() && roomCreationMode == AUTO || roomCreationMode == ALWAYS) {
                LOG.debug("create room")
                val createdRoomId = matrixClient.roomsApi.createRoom(
                        name = roomName,
                        invite = membersWithoutBot,
                        preset = TRUSTED_PRIVATE
                )

                if (!body.isNullOrBlank()) {
                    sendMessageToRoom(createdRoomId, senderId, body, receiverIds, sendAfter, sendAfterLocal)
                }
                smsBridgeProperties.templates.botSmsSendCreatedRoomAndSendMessage
            } else if (rooms.size == 1) {
                LOG.debug("only send message")
                val roomId = rooms.first().id
                if (body.isNullOrBlank()) {
                    smsBridgeProperties.templates.botSmsSendNoMessage
                } else {

                    val botIsMember = membershipService.containsMembersByRoomId( //FIXME test
                            roomId,
                            setOf("@${botProperties.username}:${botProperties.serverName}")
                    )
                    if (!botIsMember) {
                        LOG.debug("try to invite sms bot user to room $roomId")
                        matrixClient.roomsApi.inviteUser(
                                roomId = roomId,
                                userId = "@${botProperties.username}:${botProperties.serverName}",
                                asUserId = receiverIds.first()
                        )
                    }
                    sendMessageToRoom(roomId, senderId, body, receiverIds, sendAfter, sendAfterLocal)

                    smsBridgeProperties.templates.botSmsSendSendMessage
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

    private suspend fun sendMessageToRoom(
            roomId: String,
            senderId: String,
            body: String,
            receiverIds: List<String>,
            sendAfter: Instant?,
            sendAfterLocal: LocalDateTime?
    ) {
        if (sendAfter != null && Instant.now().until(sendAfter, ChronoUnit.SECONDS) > 15) {
            LOG.debug("notify room $roomId that message will be send later")
            roomService.sendRoomMessage(
                    RoomMessage(
                            roomId = roomId,
                            body = smsBridgeProperties.templates.botSmsSendNoticeDelayedMessage
                                    .replace("{sendAfter}", sendAfterLocal.toString()),
                            requiredReceiverIds = receiverIds.toSet(),
                            isNotice = true
                    )
            )
        }
        LOG.debug("send message to room $roomId")
        roomService.sendRoomMessage(
                RoomMessage(
                        roomId = roomId,
                        body = smsBridgeProperties.templates.botSmsSendNewRoomMessage
                                .replace("{sender}", senderId)
                                .replace("{body}", body),
                        requiredReceiverIds = receiverIds.toSet(),
                        sendAfter = sendAfter ?: Instant.now()
                )
        )
    }

}