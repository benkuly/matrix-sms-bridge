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
            val outgoingMessage: String = "{sender} wrote:\n\n{body}\n\nTo answer to this message add this token to " +
                                          "your message: {token}",
            val answerMissingTokenWithDefaultRoom: String? = null,
            val answerMissingTokenWithoutDefaultRoom: String? = "Your message did not contain any valid token. Nobody can and will read your message.",
            val sendSmsError: String = "Could not send sms to this user. Please try it later again.",
            val defaultRoomIncomingMessage: String = "{sender} wrote:\n{body}"
    )
}