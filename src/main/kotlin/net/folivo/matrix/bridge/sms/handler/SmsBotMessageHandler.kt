package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

// FIXME test
@Component
class SmsBotMessageHandler(
        private val roomService: SmsMatrixAppserviceRoomService,
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
        LOG.info("currently SmsBotHandler is not implemented. from: $sender room: ${room.roomId} message: $body")
        return if (body.startsWith("sms")) {
            if (room.members.size > 2) {
                context.answer(NoticeMessageEventContent(smsBridgeProperties.templates.botTooManyMembers)).then()
            } else {
                val args = body.split("\\s+".toRegex()) // FIXME not working with sms text!

                Mono.fromCallable {
                    SmsCommand()
                            .context { console = SmsBotConsole(context) }
                            .subcommands(SendSmsCommand(sender, roomService, smsBridgeProperties))
                            .main(args)
                }.subscribeOn(Schedulers.boundedElastic()).then()
            }
        } else {
            context.answer(NoticeMessageEventContent(smsBridgeProperties.templates.botHelp)).then()
        }
    }
}