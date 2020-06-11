package net.folivo.matrix.bridge.sms

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("matrix.bridge.sms")
@ConstructorBinding
data class SmsBridgeProperties(
        val templates: SmsBridgeTemplateProperties = SmsBridgeTemplateProperties(),
        val defaultRoomId: String?,
        val allowMappingWithoutToken: Boolean = true,
        val defaultRegion: String
) {
    data class SmsBridgeTemplateProperties(
            val outgoingMessage: String = "{sender} wrote:\n\n{body}",
            val outgoingMessageFromBot: String = "{body}",
            val outgoingMessageToken: String = "\n\nTo answer to this message add this token to your message: {token}",
            val answerInvalidTokenWithDefaultRoom: String? = null,
            val answerInvalidTokenWithoutDefaultRoom: String? = "Your message did not contain any valid token. Nobody can and will read your message.",
            val sendSmsError: String = "Could not send SMS to this user. Please try it again later.",
            val sendSmsIncompatibleMessage: String = "Only text messages can be send to this SMS user.",
            val defaultRoomIncomingMessage: String = "{sender} wrote:\n\n{body}",
            val botHelp: String = "To use this bot, write 'sms'",
            val botTooManyMembers: String = "Only rooms with two members are allowed to write with this bot.",
            val botSmsSendInvalidTelephoneNumber: String = "The telephone number is invalid.",
            val botSmsSendNewRoomMessage: String = "{sender} wrote:\n\n{body}",
            val botSmsSendCreatedRoomAndSendMessage: String = "You were invited to a new created room and (if given) the message to the telephone number(s) {receiverNumbers} was send for you.",
            val botSmsSendSendMessage: String = "The message was send for you into an existing room with the telephone number(s) {receiverNumbers}.",
            val botSmsSendTooManyRooms: String = "No message was sent, because there was more then one room with this telephone number(s) {receiverNumbers}. You can force room creation with the -c option.",
            val botSmsSendNoMessage: String = "There was no message content to send.",
            val botSmsSendDisabledRoomCreation: String = "No message was not sent to telephone number(s) {receiverNumbers}, because either the bot wasn't invited to the room with the telephone number or creation was disabled by your command.",
            val botSmsSendError: String = "There was an error while sending message to the telephone number(s) {receiverNumbers}. Reason: {error}"
    )
}