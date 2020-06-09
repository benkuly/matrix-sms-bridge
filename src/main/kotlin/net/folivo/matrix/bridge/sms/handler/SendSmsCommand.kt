package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.AUTO
import org.slf4j.LoggerFactory

class SendSmsCommand(
        private val sender: String,
        private val helper: SendSmsCommandHelper,
        private val smsBridgeProperties: SmsBridgeProperties
) : CliktCommand(name = "send") {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    private val phoneNumberUtil = PhoneNumberUtil.getInstance()

    private val body by argument()

    private val telephoneNumbers by option("-t", "--telephoneNumber").multiple(required = true).unique()
    private val roomName by option("-n", "--roomName")
    private val roomCreationMode by option("-m", "--roomCreationMode").enum<RoomCreationMode>().default(AUTO)
    private val useGroup by option("-g", "--group").flag()

    override fun run() {
        try {
            val receiverNumbers = telephoneNumbers.map { rawNumber ->
                phoneNumberUtil.parse(rawNumber, smsBridgeProperties.defaultRegion)
                        .let { "+${it.countryCode}${it.nationalNumber}" }
            }
            if (useGroup) {
                LOG.debug("use group and send message")
                helper.createRoomAndSendMessage(
                        body = body,
                        sender = sender,
                        receiverNumbers = receiverNumbers,
                        roomName = roomName,
                        roomCreationMode = roomCreationMode
                ).blockOptional()
                        .ifPresent { echo(it) }
            } else {
                LOG.debug("use one room for each number and send message")
                receiverNumbers.forEach { number ->
                    helper.createRoomAndSendMessage(
                            body = body,
                            sender = sender,
                            receiverNumbers = listOf(number),
                            roomName = roomName,
                            roomCreationMode = roomCreationMode
                    ).blockOptional()
                            .ifPresent { echo(it) }
                }
            }
        } catch (ex: NumberParseException) {
            LOG.debug("got NumberParseException")
            echo(smsBridgeProperties.templates.botSmsSendInvalidTelephoneNumber)
        }
    }
}