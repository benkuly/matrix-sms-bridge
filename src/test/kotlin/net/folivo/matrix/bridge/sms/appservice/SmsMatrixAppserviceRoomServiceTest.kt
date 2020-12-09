package net.folivo.matrix.bridge.sms.appservice

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import net.folivo.matrix.appservice.api.room.AppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bot.util.BotServiceHelper
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.core.model.MatrixId.RoomAliasId
import net.folivo.matrix.core.model.MatrixId.UserId
import net.folivo.matrix.restclient.api.rooms.Visibility.PRIVATE

class SmsMatrixAppserviceRoomServiceTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val botUserId = UserId("bot", "server")
        val roomServiceMock: MatrixRoomService = mockk()
        val helperMock: BotServiceHelper = mockk()
        val botPropertiesMock: MatrixBotProperties = mockk {
            every { serverName }.returns("server")
        }
        every { botPropertiesMock.botUserId }.returns(botUserId)

        val bridgePropertiesMock: SmsBridgeProperties = mockk()

        val cut = SmsMatrixAppserviceRoomService(roomServiceMock, helperMock, botPropertiesMock, bridgePropertiesMock)

        describe(SmsMatrixAppserviceRoomService::getCreateRoomParameter.name) {
            val result = cut.getCreateRoomParameter(RoomAliasId("alias", "domain"))
            val userId = UserId("alias", "server")

            it("should invite matching user and give admin rights") {
                result.invite.shouldContainExactly(userId)
                result.powerLevelContentOverride?.users?.get(userId).shouldBe(100)
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
                result.powerLevelContentOverride?.users.shouldBe(
                    mapOf(
                        botUserId to 100,
                        userId to 100
                    )
                )
            }
        }
        describe(SmsMatrixAppserviceRoomService::roomExistingState.name) {
            val roomAliasId = RoomAliasId("alias", "server")
            it("should deny room createion when single mode disabled") {
                every { bridgePropertiesMock.singleModeEnabled }.returns(false)
                cut.roomExistingState(roomAliasId).shouldBe(DOES_NOT_EXISTS)
            }
        }

        afterTest { clearMocks(bridgePropertiesMock) }
    }
}