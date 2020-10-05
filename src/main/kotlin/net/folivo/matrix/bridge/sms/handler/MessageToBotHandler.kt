package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.membership.MembershipService
import net.folivo.matrix.bridge.sms.provider.PhoneNumberService
import net.folivo.matrix.bridge.sms.user.SmsMatrixAppserviceUserService
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import org.apache.tools.ant.types.Commandline
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MessageToBotHandler(
        private val helper: SendSmsCommandHelper,
        private val phoneNumberService: PhoneNumberService,
        private val smsBridgeProperties: SmsBridgeProperties,
        private val userService: SmsMatrixAppserviceUserService,
        private val membershipService: MembershipService
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun handleMessage(
            roomId: String,
            body: String,
            senderId: String,
            context: MessageContext
    ): Boolean {
        val sender = userService.getOrCreateUser(senderId)
        val membershipSize = membershipService.getMembershipsSizeByRoomId(roomId)
        return if (sender.isManaged) {
            LOG.debug("ignore message from managed (or unknown) user")
            false
        } else if (body.startsWith("sms")) {
            if (membershipSize > 2) {
                LOG.debug("to many members in room form sms command")
                context.answer(NoticeMessageEventContent(smsBridgeProperties.templates.botTooManyMembers))
                true
            } else {
                LOG.debug("run sms command $body")

                //TODO test
                GlobalScope.launch {
                    val answerConsole = SmsBotConsole(context)
                    try {
                        val args = Commandline.translateCommandline(body.removePrefix("sms"))

                        SmsCommand().context { console = answerConsole }
                                .subcommands(
                                        SendSmsCommand(
                                                senderId,
                                                helper,
                                                phoneNumberService,
                                                smsBridgeProperties
                                        )
                                )
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
                    } catch (error: Throwable) {
                        context.answer(
                                NoticeMessageEventContent(
                                        smsBridgeProperties.templates.botSmsSendError
                                                .replace("{error}", error.message ?: "unknown")
                                                .replace("{receiverNumbers}", "unknown")
                                )
                        )
                    }
                }.join()
                true
            }
        } else if (membershipSize == 2L) {
            LOG.debug("it seems to be a bot room, but message didn't start with 'sms'")
            context.answer(NoticeMessageEventContent(smsBridgeProperties.templates.botHelp))
            true
        } else false
    }
}