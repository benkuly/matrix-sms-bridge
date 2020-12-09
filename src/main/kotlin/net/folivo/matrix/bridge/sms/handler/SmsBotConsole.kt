package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.output.CliktConsole
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.bot.event.MessageContext
import org.slf4j.LoggerFactory

class SmsBotConsole(
    private val messageContext: MessageContext
) : CliktConsole {
    override val lineSeparator: String = ""

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override fun print(text: String, error: Boolean) {
        LOG.debug("try to send answer of command: $text")
        runBlocking {
            messageContext.answer(text)
        }
    }

    override fun promptForLine(prompt: String, hideInput: Boolean): String? {
        return null
    }
}