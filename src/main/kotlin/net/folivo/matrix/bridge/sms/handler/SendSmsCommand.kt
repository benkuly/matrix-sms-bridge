package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties

// FIXME test
class SendSmsCommand(
        private val sendSmsService: SendSmsService,
        private val sender: String,
        private val messageContext: MessageContext,
        private val smsBridgeProperties: SmsBridgeProperties
) : CliktCommand(name = "send") {

    private val phoneNumberUtil = PhoneNumberUtil.getInstance()

    private val telephonNumber by argument()
    private val body by argument()

    private val roomName by option("-r", "--roomName")
    private val createNewRoom by option("-c", "--createNewRoom").flag(default = false)
    private val disableAutomaticRoomCreation by option("-d", "--disableAutomaticRoomCreation").flag(default = false)

    override fun run() {
        try {
            val receiverNumber = phoneNumberUtil
                    .parse(telephonNumber, smsBridgeProperties.defaultRegion)
                    .let { it.countryCode + it.nationalNumber }
            val answer = sendSmsService.createRoomAndSendSms(
                    body = body,
                    sender = sender,
                    receiverNumber = receiverNumber,
                    roomName = roomName,
                    createNewRoom = createNewRoom,
                    disableAutomaticRoomCreation = disableAutomaticRoomCreation
            ).blockOptional()
            if (answer.isPresent) echo(answer.get())
        } catch (ex: NumberParseException) {
            echo(smsBridgeProperties.templates.botSmsSendInvalidTelephoneNumber)
        }
        TODO("Not yet implemented")
    }
}