package net.folivo.matrix.bridge.sms.appservice

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.user.MatrixUserService
import net.folivo.matrix.bot.util.BotServiceHelper
import net.folivo.matrix.core.model.MatrixId.UserId

class SmsMatrixAppserviceUserServiceTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val userServiceMock: MatrixUserService = mockk()
        val helperMock: BotServiceHelper = mockk()
        val botPropertiesMock: MatrixBotProperties = mockk() {
            every { botUserId }.returns(UserId("bot", "server"))
        }
        val cut = SmsMatrixAppserviceUserService(userServiceMock, helperMock, botPropertiesMock)

        describe(SmsMatrixAppserviceUserService::getRegisterUserParameter.name) {
            describe("user is bot") {
                val result = cut.getRegisterUserParameter(UserId("bot", "server"))

                it("should return display name") {
                    result.displayName.shouldBe("SMS Bot")
                }
            }
            describe("user is not bot") {
                val result = cut.getRegisterUserParameter(UserId("sms_49123456", "server"))

                it("should return display name") {
                    result.displayName.shouldBe("+49123456 (SMS)")
                }
            }
        }
    }
}