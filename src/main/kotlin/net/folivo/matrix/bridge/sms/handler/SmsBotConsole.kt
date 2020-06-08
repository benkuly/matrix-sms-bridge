package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.output.CliktConsole
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import org.slf4j.LoggerFactory

// FIXME test
class SmsBotConsole(
        private val messageContext: MessageContext,
        override val lineSeparator: String = ""
) : CliktConsole {
    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override fun print(text: String, error: Boolean) {
        LOG.debug("try to send answer of command: $text")
        messageContext.answer(NoticeMessageEventContent(text)).block()
    }

    override fun promptForLine(prompt: String, hideInput: Boolean): String? {
        return null
    }
}