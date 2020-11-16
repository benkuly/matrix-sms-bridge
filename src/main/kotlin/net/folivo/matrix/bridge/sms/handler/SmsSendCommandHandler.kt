package net.folivo.matrix.bridge.sms.handler

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toSet
import net.folivo.matrix.appservice.api.AppserviceHandlerHelper
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bot.user.MatrixUserService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties.SmsBridgeTemplateProperties
import net.folivo.matrix.bridge.sms.handler.SmsSendCommand.RoomCreationMode
import net.folivo.matrix.bridge.sms.handler.SmsSendCommand.RoomCreationMode.*
import net.folivo.matrix.bridge.sms.message.MatrixMessage
import net.folivo.matrix.bridge.sms.message.MatrixMessageService
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.MatrixId.*
import net.folivo.matrix.core.model.events.m.room.NameEvent.NameEventContent
import net.folivo.matrix.core.model.events.m.room.PowerLevelsEvent.PowerLevelsEventContent
import net.folivo.matrix.restclient.MatrixClient
import net.folivo.matrix.restclient.api.rooms.Visibility.PRIVATE
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit


@Component
class SmsSendCommandHandler(
        private val userService: MatrixUserService,
        private val roomService: MatrixRoomService,
        private val membershipService: MatrixMembershipService,
        private val messageService: MatrixMessageService,
        private val appserviceHandlerHelper: AppserviceHandlerHelper,
        private val matrixClient: MatrixClient,
        private val botProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties,
) {

    private val templates: SmsBridgeTemplateProperties = smsBridgeProperties.templates

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun handleCommand(
            body: String?,
            senderId: UserId,
            receiverNumbers: Set<String>,
            inviteUserIds: Set<UserId>,
            roomName: String?,
            sendAfterLocal: LocalDateTime?,
            roomCreationMode: RoomCreationMode
    ): String {
        LOG.debug("handle command")
        val requiredManagedReceiverIds = receiverNumbers.map {
            UserId("sms_${it.removePrefix("+")}", botProperties.serverName)
        }.toSet()
        val membersWithoutBot = setOf(senderId, *requiredManagedReceiverIds.toTypedArray())
        val rooms = roomService.getRoomsByMembers(membersWithoutBot)
                .take(2)
                .map { it.id }
                .toSet()

        try {
            val answer = when (roomCreationMode) {
                AUTO -> {
                    if (smsBridgeProperties.singleModeEnabled && receiverNumbers.size == 1) {
                        sendMessageToRoomAlias(
                                senderId,
                                body,
                                roomName,
                                requiredManagedReceiverIds.first(),
                                inviteUserIds,
                                sendAfterLocal
                        )
                    } else when (rooms.size) {
                        0    -> createRoomAndSendMessage(
                                body,
                                senderId,
                                roomName,
                                requiredManagedReceiverIds,
                                inviteUserIds,
                                sendAfterLocal
                        )
                        1    -> sendMessageToRoom(
                                rooms.first(),
                                senderId,
                                body,
                                requiredManagedReceiverIds,
                                sendAfterLocal
                        )
                        else -> templates.botSmsSendTooManyRooms
                    }
                }
                ALWAYS -> {
                    createRoomAndSendMessage(
                            body,
                            senderId,
                            roomName,
                            requiredManagedReceiverIds,
                            inviteUserIds,
                            sendAfterLocal
                    )
                }
                SINGLE -> {
                    if (!smsBridgeProperties.singleModeEnabled) {
                        templates.botSmsSendSingleModeDisabled
                    } else if (receiverNumbers.size == 1) {
                        sendMessageToRoomAlias(
                                senderId,
                                body,
                                roomName,
                                requiredManagedReceiverIds.first(),
                                inviteUserIds,
                                sendAfterLocal
                        )
                    } else {
                        templates.botSmsSendSingleModeOnlyOneTelephoneNumberAllowed
                    }
                }
                NO -> {
                    when (rooms.size) {
                        0    -> templates.botSmsSendDisabledRoomCreation
                        1    -> sendMessageToRoom(
                                rooms.first(),
                                senderId,
                                body,
                                requiredManagedReceiverIds,
                                sendAfterLocal
                        )
                        else -> templates.botSmsSendTooManyRooms
                    }
                }
            }

            return answer.replace("{receiverNumbers}", receiverNumbers.joinToString())
        } catch (error: Throwable) {
            LOG.warn("trying to create room, join room or send message failed: ${error.message}")
            return templates.botSmsSendError
                    .replace("{error}", error.message ?: "unknown")
                    .replace("{receiverNumbers}", receiverNumbers.joinToString())
        }
    }

    internal suspend fun sendMessageToRoomAlias(
            senderId: UserId,
            body: String?,
            roomName: String?,
            requiredManagedReceiverId: UserId,
            inviteUserIds: Set<UserId>,
            sendAfterLocal: LocalDateTime?
    ): String {
        LOG.debug("send message to room alias")
        val aliasLocalpart = requiredManagedReceiverId.localpart
        val roomAliasId = RoomAliasId(aliasLocalpart, botProperties.serverName)
        val existingRoomId = roomService.getRoomAlias(roomAliasId)?.roomId
        val roomId = existingRoomId
                     ?: matrixClient.roomsApi.getRoomAlias(roomAliasId).roomId

        if (roomName != null && (existingRoomId == null || tryGetRoomName(roomId).isNullOrEmpty())) {
            matrixClient.roomsApi.sendStateEvent(roomId, NameEventContent(roomName))
        }
        setOf(senderId, *inviteUserIds.toTypedArray())
                .filter { !membershipService.doesRoomContainsMembers(roomId, setOf(it)) }
                .forEach { matrixClient.roomsApi.inviteUser(roomId, it) }
        
        return sendMessageToRoom(
                roomId,
                senderId,
                body,
                setOf(requiredManagedReceiverId),
                sendAfterLocal,
                denyBotInvite = true
        )
    }

    private suspend inline fun tryGetRoomName(roomId: RoomId): String? {
        return try {
            matrixClient.roomsApi.getStateEvent<NameEventContent>(roomId).name
        } catch (error: MatrixServerException) {
            null
        }
    }

    internal suspend fun createRoomAndSendMessage(
            body: String?,
            senderId: UserId,
            roomName: String?,
            requiredManagedReceiverIds: Set<UserId>,
            inviteUserIds: Set<UserId>,
            sendAfterLocal: LocalDateTime?
    ): String {
        LOG.debug("ensure that users has already been created")
        requiredManagedReceiverIds.forEach {
            if (!userService.existsUser(it)) {
                appserviceHandlerHelper.registerManagedUser(it)
            }
        }

        LOG.debug("create room and send message")
        val roomId = matrixClient.roomsApi.createRoom(
                name = roomName,
                invite = setOf(senderId, *requiredManagedReceiverIds.toTypedArray(), *inviteUserIds.toTypedArray()),
                visibility = PRIVATE,
                powerLevelContentOverride = PowerLevelsEventContent(
                        invite = 0,
                        kick = 0,
                        events = mapOf("m.room.name" to 0, "m.room.topic" to 0),
                        users = mapOf(
                                botProperties.botUserId to 100,
                                *requiredManagedReceiverIds.map { it to 100 }.toTypedArray()
                        )
                )
        )
        roomService.getOrCreateRoom(roomId)

        return if (body.isNullOrEmpty()) {
            templates.botSmsSendCreatedRoomAndSendNoMessage
        } else {
            sendMessageToRoom(roomId, senderId, body, requiredManagedReceiverIds, sendAfterLocal, true)
            templates.botSmsSendCreatedRoomAndSendMessage
        }
    }

    internal suspend fun sendMessageToRoom(
            roomId: RoomId,
            senderId: UserId,
            body: String?,
            requiredManagedReceiverIds: Set<UserId>,
            sendAfterLocal: LocalDateTime?,
            denyBotInvite: Boolean = false
    ): String {
        if (body.isNullOrBlank()) {
            return templates.botSmsSendNoMessage
        } else {
            val botIsMember = denyBotInvite || membershipService.doesRoomContainsMembers(
                    roomId,
                    setOf(botProperties.botUserId)
            )
            if (!botIsMember) {
                LOG.debug("try to invite sms bot user to room $roomId")
                matrixClient.roomsApi.inviteUser(
                        roomId = roomId,
                        userId = botProperties.botUserId,
                        asUserId = requiredManagedReceiverIds.first()
                )
            }

            val sendAfter = sendAfterLocal?.atZone(ZoneId.of(smsBridgeProperties.defaultTimeZone))?.toInstant()

            if (sendAfter != null && Instant.now().until(sendAfter, ChronoUnit.SECONDS) > 15) {
                LOG.debug("notify room $roomId that message will be send later")
                messageService.sendRoomMessage(
                        MatrixMessage(
                                roomId = roomId,
                                body = templates.botSmsSendNoticeDelayedMessage
                                        .replace("{sendAfter}", sendAfterLocal.toString()),
                                isNotice = true
                        ), requiredManagedReceiverIds.toSet()
                )
            }
            LOG.debug("send message to room $roomId")
            messageService.sendRoomMessage(
                    MatrixMessage(
                            roomId = roomId,
                            body = templates.botSmsSendNewRoomMessage
                                    .replace("{sender}", senderId.full)
                                    .replace("{body}", body),
                            sendAfter = sendAfter ?: Instant.now()
                    ), requiredManagedReceiverIds.toSet()
            )

            return templates.botSmsSendSendMessage
        }
    }

}