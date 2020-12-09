package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.CliktCommand
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.core.model.MatrixId.RoomId

class SmsAbortCommand(
    private val roomId: RoomId,
    private val handler: SmsAbortCommandHandler
) : CliktCommand(name = "abort") {

    override fun run() {
        echo(runBlocking { handler.handleCommand(roomId) })
    }
}