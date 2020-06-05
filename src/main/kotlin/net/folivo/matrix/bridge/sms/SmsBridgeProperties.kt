package net.folivo.matrix.bridge.sms

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("matrix.bridge.sms")
@ConstructorBinding
data class SmsBridgeProperties(
        val templates: SmsBridgeTemplateProperties = SmsBridgeTemplateProperties(),
        val defaultRoomId: String?,
        val defaultRegion: String
) {
    data class SmsBridgeTemplateProperties(
            val outgoingMessage: String = "{sender} wrote:\n\n{body}\n\nTo answer to this message add this token to " +
                                          "your message: {token}",
            val answerInvalidTokenWithDefaultRoom: String? = null,
            val answerInvalidTokenWithoutDefaultRoom: String? = "Your message did not contain any valid token. Nobody can and will read your message.",
            val sendSmsError: String = "Could not send SMS to this user. Please try it again later.",
            val sendSmsIncompatibleMessage: String = "Only text messages can be send to this SMS user.",
            val defaultRoomIncomingMessage: String = "{sender} wrote:\n{body}",
            val botHelp: String = "To use this bot, write sms",
            val botTooManyMembers: String = "Only two members in this room are allowed to write with this bot.",
            val botSmsSendInvalidTelephoneNumber: String = "The telephone number is invalid.",
            val botSmsSendNoRoomFound: String = "No room found with this telephone number."
    )
}