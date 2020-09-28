package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.stereotype.Service

@Service
class ReceiveSmsService(
        private val matrixClient: MatrixClient,
        private val roomService: SmsMatrixAppserviceRoomService,
        private val matrixBotProperties: MatrixBotProperties,
        private val smsBridgeProperties: SmsBridgeProperties
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun receiveSms(body: String, sender: String): String? {
        val userId =
                if (sender.matches(Regex("\\+[0-9]{6,15}"))) {
                    "@sms_${sender.substringAfter('+')}:${matrixBotProperties.serverName}"
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

        roomService.syncUserAndItsRooms(userId)
        val roomId = roomService.getRoom(
                userId = userId,
                mappingToken = mappingToken
        )?.roomId

        if (roomId != null) {
            LOG.debug("receive SMS from $sender to $roomId")
            try {
                matrixClient.roomsApi.sendRoomEvent(roomId, TextMessageEventContent(body), asUserId = userId)
                return null
            } catch (error: Throwable) {
                LOG.error("could not send SMS message to room $roomId as user $userId")
                throw error
            }
        } else {
            val defaultRoomId = smsBridgeProperties.defaultRoomId
            if (defaultRoomId != null) {
                LOG.debug("receive SMS without or wrong mappingToken from $sender to default room $defaultRoomId")
                val message = smsBridgeProperties.templates.defaultRoomIncomingMessage
                        .replace("{sender}", sender)
                        .replace("{body}", body)

                try {
                    matrixClient.roomsApi.sendRoomEvent(
                            defaultRoomId,
                            TextMessageEventContent(message)
                    )
                    return smsBridgeProperties.templates.answerInvalidTokenWithDefaultRoom.takeIf { !it.isNullOrEmpty() }
                } catch (error: Throwable) {
                    LOG.error("could not send SMS message to default room $defaultRoomId as user appservice user")
                    throw error
                }
            } else {
                return smsBridgeProperties.templates.answerInvalidTokenWithoutDefaultRoom.takeIf { !it.isNullOrEmpty() }
            }
        }
    }
}