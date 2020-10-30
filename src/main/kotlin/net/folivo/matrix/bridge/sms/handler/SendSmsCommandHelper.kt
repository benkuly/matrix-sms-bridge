package net.folivo.matrix.bridge.sms.handler

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.*
import net.folivo.matrix.bridge.sms.message.MatrixMessage
import net.folivo.matrix.bridge.sms.message.MatrixMessageService
import net.folivo.matrix.core.model.MatrixId.RoomId
import net.folivo.matrix.core.model.MatrixId.UserId
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
        private val roomService: MatrixRoomService,
        private val membershipService: MatrixMembershipService,
        private val messageService: MatrixMessageService,
        private val matrixClient: MatrixClient,
        private val botProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    enum class RoomCreationMode {
        AUTO, ALWAYS, NO, SINGLE
    }

    suspend fun createRoomAndSendMessage(
            body: String?,
            senderId: UserId,
            receiverNumbers: List<String>,
            roomName: String?,
            sendAfterLocal: LocalDateTime?,
            roomCreationMode: RoomCreationMode
    ): String {
        val receiverIds = receiverNumbers.map { UserId("@sms_${it.removePrefix("+")}", botProperties.serverName) }
        val membersWithoutBot = setOf(senderId, *receiverIds.toTypedArray())

        val rooms = roomService.getRoomsByMembers(membersWithoutBot)
                .take(2)
                .toList()

        val sendAfter = sendAfterLocal?.atZone(ZoneId.of(smsBridgeProperties.defaultTimeZone))?.toInstant()
        try {
            val answer: String
            if (roomCreationMode == SINGLE && smsBridgeProperties.singleModeEnabled) {
                LOG.debug("invite to single room")
                //FIXME
                answer = smsBridgeProperties.templates.botSmsSendCreatedRoomAndSendMessage
            } else if (rooms.isEmpty() && roomCreationMode == AUTO || roomCreationMode == ALWAYS) {
                LOG.debug("create room")
                val createdRoomId = matrixClient.roomsApi.createRoom(
                        name = roomName,
                        invite = membersWithoutBot,
                        preset = TRUSTED_PRIVATE
                )

                if (!body.isNullOrBlank()) {
                    sendMessageToRoom(createdRoomId, senderId, body, receiverIds, sendAfter, sendAfterLocal)
                }
                answer = smsBridgeProperties.templates.botSmsSendCreatedRoomAndSendMessage
            } else if (rooms.size == 1) {
                LOG.debug("only send message")
                val roomId = rooms.first().id
                if (body.isNullOrBlank()) {
                    answer = smsBridgeProperties.templates.botSmsSendNoMessage
                } else {
                    val botIsMember = membershipService.doesRoomContainsMembers( //FIXME test
                            roomId,
                            setOf(botProperties.botUserId)
                    )
                    if (!botIsMember) {
                        LOG.debug("try to invite sms bot user to room $roomId")
                        matrixClient.roomsApi.inviteUser(
                                roomId = roomId,
                                userId = botProperties.botUserId,
                                asUserId = receiverIds.first()
                        )
                    }
                    sendMessageToRoom(roomId, senderId, body, receiverIds, sendAfter, sendAfterLocal)

                    answer = smsBridgeProperties.templates.botSmsSendSendMessage
                }
            } else if (rooms.size > 1) {
                answer = smsBridgeProperties.templates.botSmsSendTooManyRooms
            } else {
                answer = smsBridgeProperties.templates.botSmsSendDisabledRoomCreation
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
            roomId: RoomId,
            senderId: UserId,
            body: String,
            receiverIds: List<UserId>,
            sendAfter: Instant?,
            sendAfterLocal: LocalDateTime?
    ) {
        if (sendAfter != null && Instant.now().until(sendAfter, ChronoUnit.SECONDS) > 15) {
            LOG.debug("notify room $roomId that message will be send later")
            messageService.sendRoomMessage(
                    MatrixMessage(
                            roomId = roomId,
                            body = smsBridgeProperties.templates.botSmsSendNoticeDelayedMessage
                                    .replace("{sendAfter}", sendAfterLocal.toString()),
                            isNotice = true
                    ), receiverIds.toSet()
            )
        }
        LOG.debug("send message to room $roomId")
        messageService.sendRoomMessage(
                MatrixMessage(
                        roomId = roomId,
                        body = smsBridgeProperties.templates.botSmsSendNewRoomMessage
                                .replace("{sender}", senderId.full)
                                .replace("{body}", body),
                        sendAfter = sendAfter ?: Instant.now()
                ), receiverIds.toSet()
        )
    }

}