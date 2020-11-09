package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktConsole
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import net.folivo.matrix.core.model.MatrixId.RoomAliasId
import net.folivo.matrix.core.model.MatrixId.UserId

class SmsInviteCommandTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val sender = UserId("sender", "server")
        val roomAliasId = RoomAliasId("alias", "server")
        val handlerMock: SmsInviteCommandHandler = mockk()
        val consoleMock: CliktConsole = mockk(relaxed = true)
        val cut = SmsInviteCommand(sender, handlerMock)
        cut.context { console = consoleMock }

        describe("alias was given") {
            coEvery { handlerMock.handleCommand(sender, roomAliasId) }.returns("answer")
            cut.parse(listOf("invite", "#alias:server"))
            coVerify { handlerMock.handleCommand(sender, roomAliasId) }
            coVerify { consoleMock.print("answer", false) }
        }
        describe("alias was not given") {
            cut.parse(listOf("invite"))
            coVerify { consoleMock.print("error", true) }
        }
        describe("alias was no alias") {
            cut.parse(listOf("invite", "!noAlias:server"))
            coVerify { consoleMock.print("error", true) }
        }
    }
}