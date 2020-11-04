package net.folivo.matrix.bridge.sms.appservice

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bot.util.BotServiceHelper
import net.folivo.matrix.core.model.MatrixId.RoomAliasId
import net.folivo.matrix.core.model.MatrixId.UserId
import net.folivo.matrix.restclient.api.rooms.Visibility.PRIVATE

class SmsMatrixAppserviceRoomServiceTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val roomServiceMock: MatrixRoomService = mockk()
        val helperMock: BotServiceHelper = mockk()
        val botPropertiesMock: MatrixBotProperties = mockk() {
            every { serverName }.returns("server")
        }
        val cut = SmsMatrixAppserviceRoomService(roomServiceMock, helperMock, botPropertiesMock)

        describe(SmsMatrixAppserviceRoomService::getCreateRoomParameter.name) {
            val result = cut.getCreateRoomParameter(RoomAliasId("alias", "domain"))

            it("should invite matching user and give admin rights") {
                result.invite.shouldContainExactly(UserId("alias", "server"))
                result.powerLevelContentOverride?.users?.get(UserId("alias", "server")).shouldBe(100)
            }
            it("visibility should be private") {
                result.visibility.shouldBe(PRIVATE)
            }
            it("user rights should be set") {
                result.powerLevelContentOverride?.invite.shouldBe(0)
                result.powerLevelContentOverride?.kick.shouldBe(0)
                result.powerLevelContentOverride?.events.shouldNotBeNull()
                result.powerLevelContentOverride?.events?.shouldContainExactly(
                        mapOf("m.room.name" to 0, "m.room.topic" to 0)
                )
            }
        }
    }
}