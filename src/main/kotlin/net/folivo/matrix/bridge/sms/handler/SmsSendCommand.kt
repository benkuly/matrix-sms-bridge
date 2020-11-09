package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.google.i18n.phonenumbers.NumberParseException
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.SmsSendCommand.RoomCreationMode.AUTO
import net.folivo.matrix.bridge.sms.provider.PhoneNumberService
import net.folivo.matrix.core.model.MatrixId.UserId
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class SmsSendCommand(
        private val sender: UserId,
        private val handler: SmsSendCommandHandler,
        private val phoneNumberService: PhoneNumberService,
        private val smsBridgeProperties: SmsBridgeProperties
) : CliktCommand(name = "send") {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    private val body by argument("body").optional()

    private val telephoneNumbers by option("-t", "--telephoneNumber").multiple(required = true).unique()
    private val roomName by option("-n", "--roomName")
    private val roomCreationMode by option("-m", "--roomCreationMode").enum<RoomCreationMode>().default(AUTO)
    private val useGroup by option("-g", "--group").flag()
    private val sendAfter by option("-a", "--sendAfter").convert { LocalDateTime.parse(it) }

    enum class RoomCreationMode {
        AUTO, ALWAYS, NO, SINGLE
    }

    override fun run() {
        try {
            val receiverNumbers = telephoneNumbers.map { phoneNumberService.parseToInternationalNumber(it) }
            if (useGroup) {
                LOG.debug("use group and send message")
                echo(runBlocking {
                    handler.handleCommand(
                            body = body,
                            senderId = sender,
                            receiverNumbers = receiverNumbers.toSet(),
                            roomName = roomName,
                            roomCreationMode = roomCreationMode,
                            sendAfterLocal = sendAfter
                    )
                })
            } else {
                LOG.debug("use one room for each number and send message")
                receiverNumbers.forEach { number ->
                    echo(
                            runBlocking {
                                handler.handleCommand(
                                        body = body,
                                        senderId = sender,
                                        receiverNumbers = setOf(number),
                                        roomName = roomName,
                                        roomCreationMode = roomCreationMode,
                                        sendAfterLocal = sendAfter
                                )
                            })
                }
            }
        } catch (ex: NumberParseException) {
            LOG.debug("got NumberParseException")
            echo(smsBridgeProperties.templates.botSmsSendInvalidTelephoneNumber)
        }
    }
}