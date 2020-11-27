package net.folivo.matrix.bridge.sms.handler

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.message.MatrixMessageService
import net.folivo.matrix.core.model.MatrixId.RoomId

class SmsAbortCommandHandlerTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val messageServiceMock: MatrixMessageService = mockk()
        val smsBridgePropertiesMock: SmsBridgeProperties = mockk {
            every { templates.botSmsAbortSuccess }.returns("success")
            every { templates.botSmsAbortError }.returns("error:{error}")
        }
        val cut = SmsAbortCommandHandler(messageServiceMock, smsBridgePropertiesMock)

        val roomId = RoomId("room", "server")

        describe(SmsAbortCommandHandler::handleCommand.name) {
            it("should delete messages") {
                coEvery { messageServiceMock.deleteByRoomId(roomId) } just Runs
                cut.handleCommand(roomId).shouldBe("success")
                coVerify { messageServiceMock.deleteByRoomId(roomId) }
            }
            it("should catch error") {
                coEvery { messageServiceMock.deleteByRoomId(roomId) }.throws(RuntimeException("meteor"))
                cut.handleCommand(roomId).shouldBe("error:meteor")
            }
        }

        afterTest { clearMocks(messageServiceMock) }
    }
}