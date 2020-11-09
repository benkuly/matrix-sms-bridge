package net.folivo.matrix.bridge.sms.handler

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.core.model.MatrixId.*
import net.folivo.matrix.restclient.MatrixClient

class SmsInviteCommandHelperTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val roomServiceMock: MatrixRoomService = mockk()
        val matrixClientMock: MatrixClient = mockk()
        val smsBridgePropertiesMock: SmsBridgeProperties = mockk {
            every { templates.botSmsInviteSuccess }.returns("{sender} invited to {roomAlias}")
            every { templates.botSmsInviteError }.returns("{sender} not invited to {roomAlias}:{error}")
        }
        val cut = SmsInviteCommandHelper(roomServiceMock, matrixClientMock, smsBridgePropertiesMock)

        val senderId = UserId("sender", "server")
        val aliasId = RoomAliasId("alias", "server")
        val roomId = RoomId("room", "server")

        describe("alias does exists in database") {
            beforeTest { coEvery { roomServiceMock.getRoomAlias(aliasId)?.roomId }.returns(roomId) }
            describe("invite is successful") {
                beforeTest {
                    coEvery { matrixClientMock.roomsApi.inviteUser(any(), any()) } just Runs
                }
                it("should invite and answer") {
                    cut.handleCommand(senderId, aliasId).shouldBe("@sender:server invited to #alias:server")
                    coVerify { matrixClientMock.roomsApi.inviteUser(roomId, senderId) }
                }
            }
            describe("invite is not successful") {
                beforeTest {
                    coEvery { matrixClientMock.roomsApi.inviteUser(roomId, senderId) }.throws(RuntimeException("error"))
                }
                it("should invite and answer") {
                    cut.handleCommand(senderId, aliasId).shouldBe("@sender:server not invited to #alias:server:error")
                }
            }
        }
        describe("alias does not exists in database") {
            beforeTest {
                coEvery { roomServiceMock.getRoomAlias(any()) }.returns(null)
                coEvery { matrixClientMock.roomsApi.getRoomAlias(aliasId).roomId }.returns(roomId)
                coEvery { matrixClientMock.roomsApi.inviteUser(any(), any()) } just Runs
            }
            it("should invite with room id from server") {
                cut.handleCommand(senderId, aliasId)
                coVerify { matrixClientMock.roomsApi.inviteUser(roomId, senderId) }
            }
        }

        afterTest { clearMocks(roomServiceMock, matrixClientMock, smsBridgePropertiesMock) }

    }
}