package net.folivo.matrix.bridge.sms

import net.folivo.matrix.core.model.MatrixId.RoomId
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("matrix.bridge.sms")
@ConstructorBinding
data class SmsBridgeProperties(
        val templates: SmsBridgeTemplateProperties = SmsBridgeTemplateProperties(),
        val defaultRoomId: RoomId?,
        val allowMappingWithoutToken: Boolean = true,
        val singleModeEnabled: Boolean = false,
        val defaultRegion: String,
        val defaultTimeZone: String = "UTC"
) {
    data class SmsBridgeTemplateProperties(
            val outgoingMessage: String = "{sender} wrote:\n\n{body}",
            val outgoingMessageFromBot: String = "{body}",//FIXME bad
            val outgoingMessageToken: String = "\n\nTo answer to this message add this token to your message: {token}",
            val answerInvalidTokenWithDefaultRoom: String? = null,
            val answerInvalidTokenWithoutDefaultRoom: String? = "Your message did not contain any valid token. Nobody can and will read your message.",
            val sendSmsError: String = "Could not send SMS: {error}",
            val sendSmsIncompatibleMessage: String = "Only text messages can be send to this SMS user.",
            val defaultRoomIncomingMessage: String = "{sender} wrote:\n\n{body}",
            val defaultRoomIncomingMessageWithSingleMode: String = "A message from {sender} was send to room {roomAlias}. Someone should join the room. Otherwise nobody will read the message.\n\nType `sms invite {roomAlias}` in a bot room to get invited to the room.",
            val botHelp: String = "To use this bot, type 'sms'",
            val botTooManyMembers: String = "Only rooms with two members are allowed to write with this bot.",
            val botSmsError: String = "There was an error while using sms command. Reason: {error}",
            val botSmsSendInvalidTelephoneNumber: String = "The telephone number is invalid.",
            val botSmsSendNewRoomMessage: String = "{sender} wrote:\n\n{body}",//FIXME bad
            val botSmsSendNoticeDelayedMessage: String = "A message will be sent for you at {sendAfter}.",
            val botSmsSendCreatedRoomAndSendMessage: String = "You were invited to a new created room and (if given) the message to the telephone number(s) {receiverNumbers} was (or will be) sent for you.",
            val botSmsSendCreatedRoomAndSendNoMessage: String = "You were invited to a new created room.",
            val botSmsSendSendMessage: String = "The message was (or will be) sent for you into an existing room with the telephone number(s) {receiverNumbers}.",
            val botSmsSendTooManyRooms: String = "No message was (or will be) sent, because there was more then one room with this telephone number(s) {receiverNumbers}. You can force room creation with the -c option.",
            val botSmsSendNoMessage: String = "There was no message content to send.",
            val botSmsSendDisabledRoomCreation: String = "No message was sent to telephone number(s) {receiverNumbers}, because either the bot wasn't invited to the room with the telephone number or creation was disabled by your command.",
            val botSmsSendSingleModeOnlyOneTelephoneNumberAllowed: String = "Single mode is allowed with one telephone number only.",
            val botSmsSendSingleModeDisabled: String = "Single mode was disabled by your admin.",
            val botSmsSendError: String = "There was an error while sending message to the telephone number(s) {receiverNumbers}. Reason: {error}",
            val botSmsInviteSuccess: String = "{sender} was invited to {roomAlias}.",
            val botSmsInviteError: String = "There was an error while invite {sender} to {roomAlias}. Reason: {error}",
            val providerSendError: String = "Could not send sms to {receiver} with your provider. We will try to resend it and will notify you as soon as it was successful. Reason: {error}",
            val providerResendSuccess: String = "The resend was successful for all messages."
    )
}