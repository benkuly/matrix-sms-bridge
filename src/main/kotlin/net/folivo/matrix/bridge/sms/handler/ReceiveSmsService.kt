package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.mapping.MatrixSmsMappingService
import net.folivo.matrix.core.model.MatrixId.RoomAliasId
import net.folivo.matrix.core.model.MatrixId.UserId
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
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
                    throw IllegalArgumentException("The sender did not match our regex for international telephone numbers.")
                }

        val mappingTokenMatch = Regex("#[0-9]{1,9}").find(body)
        val mappingToken = mappingTokenMatch?.value?.substringAfter('#')?.toInt()

        val cleanedBody = mappingTokenMatch?.let { body.removeRange(it.range) }?.trim() ?: body

        val roomIdFromMappingToken = mappingService.getRoomId(
                userId = userId,
                mappingToken = mappingToken
        )
        if (roomIdFromMappingToken != null) {
            LOG.debug("receive SMS from $sender to $roomIdFromMappingToken")
            matrixClient.roomsApi.sendRoomEvent(
                    roomIdFromMappingToken,
                    TextMessageEventContent(cleanedBody),
                    asUserId = userId
            )
            return null
        } else if (smsBridgeProperties.singleModeEnabled) {
            LOG.debug("receive SMS without or wrong mappingToken from $sender to single room")
            val roomAliasId = RoomAliasId(userIdLocalpart, matrixBotProperties.serverName)
            val roomIdFromAlias = roomService.getRoomAlias(roomAliasId)?.roomId
                                  ?: matrixClient.roomsApi.getRoomAlias(roomAliasId).roomId // FIXME does this work?
            matrixClient.roomsApi.sendRoomEvent(
                    roomIdFromAlias,
                    TextMessageEventContent(cleanedBody),
                    asUserId = userId
            )
            if (membershipService.hasRoomOnlyManagedUsersLeft(roomIdFromAlias)) {
                if (defaultRoomId != null) {
                    val message = templates.defaultRoomIncomingMessageWithSingleMode
                            .replace("{sender}", sender)
                            .replace("{roomAlias}", roomAliasId.toString())
                    matrixClient.roomsApi.sendRoomEvent(
                            defaultRoomId,
                            TextMessageEventContent(message)
                    )
                } else return templates.answerInvalidTokenWithoutDefaultRoom.takeIf { !it.isNullOrEmpty() }
            }
            return null
        } else {
            LOG.debug("receive SMS without or wrong mappingToken from $sender to default room $defaultRoomId")

            return if (defaultRoomId != null) {
                val message = templates.defaultRoomIncomingMessage
                        .replace("{sender}", sender)
                        .replace("{body}", cleanedBody)

                matrixClient.roomsApi.sendRoomEvent(
                        defaultRoomId,
                        TextMessageEventContent(message)
                )
                templates.answerInvalidTokenWithDefaultRoom.takeIf { !it.isNullOrEmpty() }
            } else templates.answerInvalidTokenWithoutDefaultRoom.takeIf { !it.isNullOrEmpty() }
        }
    }
}