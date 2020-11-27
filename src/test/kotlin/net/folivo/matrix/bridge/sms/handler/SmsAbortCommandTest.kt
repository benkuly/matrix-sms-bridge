package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktConsole
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import net.folivo.matrix.core.model.MatrixId.RoomId

class SmsAbortCommandTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val roomId = RoomId("room", "server")
        val handlerMock: SmsAbortCommandHandler = mockk()
        val consoleMock: CliktConsole = mockk(relaxed = true)
        val cut = SmsAbortCommand(roomId, handlerMock)
        cut.context { console = consoleMock }

        describe("run command") {
            it("should run command") {
                coEvery { handlerMock.handleCommand(roomId) }.returns("answer")
                cut.parse(listOf())
                coVerify { handlerMock.handleCommand(roomId) }
                coVerify { consoleMock.print("answer", false) }
            }
        }
        
        afterTest { clearMocks(consoleMock) }
    }
}