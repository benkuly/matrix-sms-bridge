package net.folivo.matrix.bridge.sms

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("matrix.sms.bridge")
@ConstructorBinding
data class SmsBridgeProperties(
        val templates: SmsBridgeTemplateProperties,
        val defaultRoomId: String?
) {
    data class SmsBridgeTemplateProperties(
            val outgoingMessage: String = "{sender} wrote:\n{body}\n\nTo answer to this message add this token to " +
                                          "your message: {token}",
            val wrongToken: String = "Your message did not contain a valid token. If the admin of this bridge to matrix " +
                                     "enabled a default room for messages without a valid token you have nothing to do. " +
                                     "Someone will read your message."
    )
}