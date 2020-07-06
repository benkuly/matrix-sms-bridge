package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import org.apache.tools.ant.types.Commandline
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SmsBotMessageHandler(
        private val helper: SendSmsCommandHelper,
        private val smsBridgeProperties: SmsBridgeProperties
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun handleMessageToSmsBot(
            room: AppserviceRoom,
            body: String,
            sender: String,
            context: MessageContext
    ): Boolean = coroutineScope {
        if (room.members.keys.find { it.userId == sender && !it.isManaged } == null) {
            LOG.debug("ignore message from not managed user")
            false
        } else if (body.startsWith("sms")) {
            if (room.members.size > 2) {
                LOG.debug("to many members in room form sms command")
                context.answer(NoticeMessageEventContent(smsBridgeProperties.templates.botTooManyMembers))
                true
            } else {
                LOG.debug("run sms command $body")

                val args = Commandline.translateCommandline(body.removePrefix("sms"))

                //TODO test
                launch {
                    val answerConsole = SmsBotConsole(context)
                    try {
                        SmsCommand().context { console = answerConsole }
                                .subcommands(SendSmsCommand(sender, helper, smsBridgeProperties))
                                .parse(args)
                    } catch (e: PrintHelpMessage) {
                        answerConsole.print(e.command.getFormattedHelp(), false)
                    } catch (e: PrintCompletionMessage) {
                        e.message?.also { answerConsole.print(it, false) }
                    } catch (e: PrintMessage) {
                        e.message?.also { answerConsole.print(it, false) }
                    } catch (e: UsageError) {
                        answerConsole.print(e.helpMessage(), true)
                    } catch (e: CliktError) {
                        e.message?.also { answerConsole.print(it, true) }
                    } catch (e: Abort) {
                        answerConsole.print("Aborted!", true)
                    }
                }.join()
                true
            }
        } else if (room.members.size == 2) {
            LOG.debug("it seems to be a bot room, but message didn't start with 'sms'")
            context.answer(NoticeMessageEventContent(smsBridgeProperties.templates.botHelp))
            true
        } else false
    }
}