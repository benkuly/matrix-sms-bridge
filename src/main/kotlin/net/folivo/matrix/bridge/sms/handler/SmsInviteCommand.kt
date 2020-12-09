package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.core.model.MatrixId.RoomAliasId
import net.folivo.matrix.core.model.MatrixId.UserId

class SmsInviteCommand(
    private val sender: UserId,
    private val handler: SmsInviteCommandHandler
) : CliktCommand(name = "invite") {

    private val alias by argument("alias").convert { RoomAliasId(it) }

    override fun run() {
        echo(runBlocking { handler.handleCommand(sender, alias) })
    }
}