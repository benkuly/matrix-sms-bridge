package net.folivo.matrix.bridge.sms

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("matrix.bridge.sms")
@ConstructorBinding
data class SmsBridgeProperties(
        val templates: SmsBridgeTemplateProperties = SmsBridgeTemplateProperties(),
        val defaultRoomId: String?
) {
    data class SmsBridgeTemplateProperties(
            val outgoingMessage: String = "{sender} wrote:\n{body}\n\nTo answer to this message add this token to " +
                                          "your message: {token}",
            val missingTokenWithDefaultRoom: String? = "Your message did not contain any valid token. Your messages will be forwarded to a default matrix room.",
            val missingTokenWithoutDefaultRoom: String? = "Your message did not contain any valid token. Nobody can and will read your message."
    )
}