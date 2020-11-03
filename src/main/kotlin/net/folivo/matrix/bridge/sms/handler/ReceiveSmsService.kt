package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.mapping.MatrixSmsMappingService
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.MatrixId.RoomAliasId
import net.folivo.matrix.core.model.MatrixId.UserId
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.stereotype.Service

@Service
class ReceiveSmsService(
        private val matrixClient: MatrixClient,
        private val mappingService: MatrixSmsMappingService,
        private val membershipService: MatrixMembershipService,
        private val roomService: MatrixRoomService,
        private val matrixBotProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties
) {

    private val templates = smsBridgeProperties.templates
    private val defaultRoomId = smsBridgeProperties.defaultRoomId

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun receiveSms(body: String, sender: String): String? {
        val userIdLocalpart = "sms_${sender.substringAfter('+')}"
        val userId =
                if (sender.matches(Regex("\\+[0-9]{6,15}"))) {
                    UserId(userIdLocalpart, matrixBotProperties.serverName)
                } else {
                    throw MatrixServerException(
                            BAD_REQUEST,
                            ErrorResponse(
                                    "NET.FOLIVO_BAD_REQUEST",
                                    "The sender did not match our regex for international telephone numbers."
                            )
                    )
                }

        val mappingToken = Regex("#[0-9]{1,9}").find(body)
                ?.value?.substringAfter('#')?.toInt()

        val roomIdFromMappingToken = mappingService.getRoomId(
                userId = userId,
                mappingToken = mappingToken
        )
        if (roomIdFromMappingToken != null) {
            LOG.debug("receive SMS from $sender to $roomIdFromMappingToken")
            matrixClient.roomsApi.sendRoomEvent(
                    roomIdFromMappingToken,
                    TextMessageEventContent(body),
                    asUserId = userId
            )
            return null
        } else if (smsBridgeProperties.singleModeEnabled) {
            LOG.debug("receive SMS without or wrong mappingToken from $sender to single room")
            val roomAliasId = RoomAliasId(userIdLocalpart, matrixBotProperties.serverName)
            val roomIdFromAlias = roomService.getRoomAlias(roomAliasId)?.roomId
                                  ?: matrixClient.roomsApi.getRoomAlias(roomAliasId).roomId // does this work?
            matrixClient.roomsApi.sendRoomEvent(
                    roomIdFromAlias,
                    TextMessageEventContent(body),
                    asUserId = userId
            )
            if (membershipService.hasRoomOnlyManagedUsersLeft(roomIdFromAlias)) {
                if (defaultRoomId != null) {
                    val message = templates.defaultRoomIncomingMessage
                            .replace("{sender}", sender)
                            .replace("{roomAlias}", roomAliasId.toString())
                    matrixClient.roomsApi.sendRoomEvent(
                            defaultRoomId,
                            TextMessageEventContent(message)
                    )
                } else {
                    return templates.answerInvalidTokenWithoutDefaultRoom.takeIf { !it.isNullOrEmpty() }
                }
            }
            return null
        } else {
            if (defaultRoomId != null) {
                LOG.debug("receive SMS without or wrong mappingToken from $sender to default room $defaultRoomId")
                val message = templates.defaultRoomIncomingMessage
                        .replace("{sender}", sender)
                        .replace("{body}", body)

                matrixClient.roomsApi.sendRoomEvent(
                        defaultRoomId,
                        TextMessageEventContent(message)
                )
                return templates.answerInvalidTokenWithDefaultRoom.takeIf { !it.isNullOrEmpty() }
            } else {
                return templates.answerInvalidTokenWithoutDefaultRoom.takeIf { !it.isNullOrEmpty() }
            }
        }
    }
}