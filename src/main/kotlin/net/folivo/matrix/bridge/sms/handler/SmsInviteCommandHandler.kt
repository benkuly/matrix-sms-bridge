package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties.SmsBridgeTemplateProperties
import net.folivo.matrix.core.model.MatrixId.RoomAliasId
import net.folivo.matrix.core.model.MatrixId.UserId
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


@Component
class SmsInviteCommandHandler(
    private val roomService: MatrixRoomService,
    private val matrixClient: MatrixClient,
    smsBridgeProperties: SmsBridgeProperties,
) {

    private val templates: SmsBridgeTemplateProperties = smsBridgeProperties.templates

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun handleCommand(
        sender: UserId,
        alias: RoomAliasId
    ): String {
        return try {
            val roomId = roomService.getRoomAlias(alias)?.roomId
                ?: matrixClient.roomsApi.getRoomAlias(alias).roomId
            matrixClient.roomsApi.inviteUser(roomId, sender)
            templates.botSmsInviteSuccess
                .replace("{roomAlias}", alias.full)
                .replace("{sender}", sender.full)
        } catch (ex: Throwable) {
            LOG.debug("got exception")
            templates.botSmsInviteError
                .replace("{roomAlias}", alias.full)
                .replace("{sender}", sender.full)
                .replace("{error}", ex.message ?: "unknown")
        }
    }
}