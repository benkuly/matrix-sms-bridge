package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import org.apache.tools.ant.types.Commandline
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@Component
class SmsBotMessageHandler(
        private val helper: SendSmsCommandHelper,
        private val smsBridgeProperties: SmsBridgeProperties
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    fun handleMessageToSmsBot(
            room: AppserviceRoom,
            body: String,
            sender: String,
            context: MessageContext
    ): Mono<Void> {
        return if (body.startsWith("sms")) {
            if (room.members.size > 2) {
                LOG.debug("to many members in room form sms command")
                context.answer(NoticeMessageEventContent(smsBridgeProperties.templates.botTooManyMembers)).then()
            } else {
                LOG.debug("run sms command $body")

                val args = Commandline.translateCommandline(body.removePrefix("sms"))

                Mono.fromCallable {
                    SmsCommand()
                            .context { console = SmsBotConsole(context) }
                            .subcommands(SendSmsCommand(sender, helper, smsBridgeProperties))
                            .parse(args)
                }.subscribeOn(Schedulers.boundedElastic()).then()
            }
        } else if (room.members.size == 2) {
            LOG.debug("it seems to be a bot room, but message didn't start with 'sms'")
            context.answer(NoticeMessageEventContent(smsBridgeProperties.templates.botHelp)).then()
        } else Mono.empty()
    }
}