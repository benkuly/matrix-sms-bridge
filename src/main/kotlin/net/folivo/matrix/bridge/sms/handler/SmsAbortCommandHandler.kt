package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties.SmsBridgeTemplateProperties
import net.folivo.matrix.bridge.sms.message.MatrixMessageService
import net.folivo.matrix.core.model.MatrixId.RoomId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


@Component
class SmsAbortCommandHandler(
    private val messageService: MatrixMessageService,
    smsBridgeProperties: SmsBridgeProperties,
) {

    private val templates: SmsBridgeTemplateProperties = smsBridgeProperties.templates

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun handleCommand(
        roomId: RoomId
    ): String {
        return try {
            messageService.deleteByRoomId(roomId)
            templates.botSmsAbortSuccess
        } catch (ex: Throwable) {
            LOG.debug("got exception")
            templates.botSmsAbortError
                .replace("{error}", ex.message ?: "unknown")
        }
    }

}