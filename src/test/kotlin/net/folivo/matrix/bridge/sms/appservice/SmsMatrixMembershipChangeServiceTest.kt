package net.folivo.matrix.bridge.sms.appservice

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.room.MatrixRoomAlias
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.core.model.MatrixId.*

class SmsMatrixMembershipChangeServiceTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val roomServiceMock: MatrixRoomService = mockk()
        val botPropertiesMock: MatrixBotProperties = mockk {
            every { botUserId } returns UserId("bot", "server")
        }

        val cut = SmsMatrixMembershipChangeService(
                roomServiceMock,
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                botPropertiesMock
        )

        val roomId = RoomId("room", "server")
        describe(SmsMatrixMembershipChangeService::shouldJoinRoom.name) {
            it("user is bot") {
                cut.shouldJoinRoom(UserId("bot", "server"), roomId)
                        .shouldBeTrue()
            }
            it("alias is that from user") {
                coEvery { roomServiceMock.getRoomAliasByRoomId(roomId) }
                        .returns(MatrixRoomAlias(RoomAliasId("sms_111111", "server"), roomId))
                cut.shouldJoinRoom(UserId("sms_111111", "server"), roomId)
                        .shouldBeTrue()
            }
            it("alias is not that from user") {
                coEvery { roomServiceMock.getRoomAliasByRoomId(roomId) }
                        .returns(MatrixRoomAlias(RoomAliasId("sms_222222", "server"), roomId))
                cut.shouldJoinRoom(UserId("sms_111111", "server"), roomId)
                        .shouldBeFalse()
            }
        }


        afterTest { clearMocks(roomServiceMock) }
    }
}