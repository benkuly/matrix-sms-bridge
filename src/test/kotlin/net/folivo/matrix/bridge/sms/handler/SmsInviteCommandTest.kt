package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.MissingArgument
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktConsole
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
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
            cut.parse(listOf("#alias:server"))
            coVerify { handlerMock.handleCommand(sender, roomAliasId) }
            coVerify { consoleMock.print("answer", false) }
        }
        describe("alias was not given") {
            shouldThrow<MissingArgument> {
                cut.parse(listOf())
            }
        }
        describe("alias was no alias") {
            shouldThrow<BadParameterValue> {
                cut.parse(listOf("!noAlias:server"))
            }
        }

        afterTest { clearMocks(consoleMock) }
    }
}