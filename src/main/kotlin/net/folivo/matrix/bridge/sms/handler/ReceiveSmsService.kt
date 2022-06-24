package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.mapping.MatrixSmsMappingService
import net.folivo.matrix.bridge.sms.message.MatrixMessage
import net.folivo.matrix.bridge.sms.message.MatrixMessageService
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
    private val messageService: MatrixMessageService,
    private val membershipService: MatrixMembershipService,
    private val roomService: MatrixRoomService,
    private val phoneNumberService: PhoneNumberService,
    private val matrixBotProperties: MatrixBotProperties,
    private val smsBridgeProperties: SmsBridgeProperties
) {

    private val templates = smsBridgeProperties.templates

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun receiveSms(body: String, providerSender: String): String? {
        if (phoneNumberService.isAlphanumeric(providerSender)) {
            sendMessageFromInvalidNumberToDefaultRoom(body, providerSender)
            return null
        } else {
            val sender: String
            try {
                sender = phoneNumberService.parseToInternationalNumber(providerSender)
            } catch (error: Throwable) {
                LOG.debug("could not parse to international number", error)
                sendMessageFromInvalidNumberToDefaultRoom(body, providerSender)
                return null
            }

            val userIdLocalpart = "${smsBridgeProperties.defaultLocalPart}${sender.removePrefix("+")}"
            val userId = UserId(userIdLocalpart, matrixBotProperties.serverName)

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
                    ?: matrixClient.roomsApi.getRoomAlias(roomAliasId).roomId

                messageService.sendRoomMessage(
                    MatrixMessage(roomIdFromAlias, cleanedBody, isNotice = false, asUserId = userId)
                )

                if (membershipService.hasRoomOnlyManagedUsersLeft(roomIdFromAlias)) {
                    if (smsBridgeProperties.defaultRoomId != null) {
                        val message = templates.defaultRoomIncomingMessageWithSingleMode
                            .replace("{sender}", sender)
                            .replace("{roomAlias}", roomAliasId.toString())
                        matrixClient.roomsApi.sendRoomEvent(
                            smsBridgeProperties.defaultRoomId,
                            TextMessageEventContent(message)
                        )
                    } else return templates.answerInvalidTokenWithoutDefaultRoom.takeIf { !it.isNullOrEmpty() }
                }
                return null
            } else {
                LOG.debug("receive SMS without or wrong mappingToken from $sender to default room ${smsBridgeProperties.defaultRoomId}")

                return if (smsBridgeProperties.defaultRoomId != null) {
                    val message = templates.defaultRoomIncomingMessage
                        .replace("{sender}", sender)
                        .replace("{body}", cleanedBody)

                    matrixClient.roomsApi.sendRoomEvent(
                        smsBridgeProperties.defaultRoomId,
                        TextMessageEventContent(message)
                    )
                    templates.answerInvalidTokenWithDefaultRoom.takeIf { !it.isNullOrEmpty() }
                } else templates.answerInvalidTokenWithoutDefaultRoom.takeIf { !it.isNullOrEmpty() }
            }
        }
    }

    private suspend fun sendMessageFromInvalidNumberToDefaultRoom(body: String, providerSender: String) {
        if (smsBridgeProperties.defaultRoomId != null) {
            LOG.debug("receive SMS with invalid or alphanumeric number from sender $providerSender to default room ${smsBridgeProperties.defaultRoomId}")
            val message = templates.defaultRoomIncomingMessage
                .replace("{sender}", providerSender)
                .replace("{body}", body)

            matrixClient.roomsApi.sendRoomEvent(
                smsBridgeProperties.defaultRoomId,
                TextMessageEventContent(message)
            )
        } else {
            LOG.warn("you got a message from an alphanumeric or invalid sender. You should enable default room to receive this message regular: $providerSender sent: $body")
        }
    }
}
