package net.folivo.matrix.bridge.sms.handler

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.*
import net.folivo.matrix.bot.event.MessageContext
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bot.user.MatrixUser
import net.folivo.matrix.bot.user.MatrixUserService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.provider.PhoneNumberService
import net.folivo.matrix.core.model.MatrixId.*

class MessageToBotHandlerTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val smsSendCommandHelperMock: SmsSendCommandHelper = mockk()
        val smsInviteCommandHelperMock: SmsInviteCommandHelper = mockk()
        val phoneNumberServiceMock: PhoneNumberService = mockk()
        val smsBridgePropertiesMock: SmsBridgeProperties = mockk {
            every { templates.botHelp }.returns("help")
            every { templates.botSmsError }.returns("error")
            every { templates.botTooManyMembers }.returns("toMany")
        }
        val userServiceMock: MatrixUserService = mockk()
        val membershipServiceMock: MatrixMembershipService = mockk()

        val cut = MessageToBotHandler(
                smsSendCommandHelperMock,
                smsInviteCommandHelperMock,
                phoneNumberServiceMock,
                smsBridgePropertiesMock,
                userServiceMock,
                membershipServiceMock
        )

        val contextMock: MessageContext = mockk {
            coEvery { answer(any<String>(), any()) }.returns(EventId("message", "server"))
        }
        val roomId = RoomId("room", "server")
        val senderId = UserId("sender", "server")

        describe(MessageToBotHandler::handleMessage.name) {
            describe("sender is managed") {
                it("should do nothing and return false") {
                    coEvery { userServiceMock.getOrCreateUser(senderId) }.returns(MatrixUser(senderId, true))
                    coEvery { membershipServiceMock.getMembershipsSizeByRoomId(roomId) }.returns(2L)
                    cut.handleMessage(roomId, "sms", senderId, contextMock).shouldBeFalse()
                    coVerifyAll {
                        smsSendCommandHelperMock wasNot Called
                        smsInviteCommandHelperMock wasNot Called
                    }
                }
            }
            describe("to many members in room") {
                it("should warn user and return true") {
                    coEvery { userServiceMock.getOrCreateUser(senderId) }.returns(MatrixUser(senderId))
                    coEvery { membershipServiceMock.getMembershipsSizeByRoomId(roomId) }.returns(3L)
                    cut.handleMessage(roomId, "sms", senderId, contextMock).shouldBeTrue()
                    coVerifyAll {
                        smsSendCommandHelperMock wasNot Called
                        smsInviteCommandHelperMock wasNot Called
                        contextMock.answer("toMany")
                    }
                }
            }
            describe("valid sms command") {
                beforeTest {
                    coEvery { userServiceMock.getOrCreateUser(senderId) }.returns(MatrixUser(senderId))
                    coEvery { membershipServiceMock.getMembershipsSizeByRoomId(roomId) }.returns(2L)
                }
                it("should run sms send command") {
                    coEvery { smsSendCommandHelperMock.handleCommand(any(), any(), any(), any(), any(), any()) }
                            .returns("message send")
                    every { smsBridgePropertiesMock.defaultRegion }.returns("DE")
                    every { phoneNumberServiceMock.parseToInternationalNumber(any()) }.returns("+4917392837462")
                    cut.handleMessage(
                            roomId,
                            "sms send -t 017392837462 'some Text'",
                            senderId,
                            contextMock
                    ).shouldBeTrue()

                    coVerify(exactly = 1) {
                        contextMock.answer("message send")
                    }
                }
                it("should run sms invite command") {
                    coEvery { smsInviteCommandHelperMock.handleCommand(any(), any()) }
                            .returns("invited")
                    cut.handleMessage(
                            roomId,
                            "sms invite #sms_1739283746:server",
                            senderId,
                            contextMock
                    ).shouldBeTrue()

                    coVerify(exactly = 1) {
                        contextMock.answer("invited")
                    }
                }
                it("should catch errors from command") {
                    cut.handleMessage(roomId, "sms send bla", senderId, contextMock).shouldBeTrue()
                    coVerify {
                        contextMock.answer(match<String> { it.contains("Error") })
                    }
                }
                it("should catch errors from unparsable command") {
                    cut.handleMessage(roomId, "sms send \" bla", senderId, contextMock).shouldBeTrue()
                    coVerify {
                        contextMock.answer("error unbalanced quotes in  send \" bla unknown")
                    }
                }
            }
            describe("two members but no sms command") {
                it("should warn user and return true") {
                    coEvery { userServiceMock.getOrCreateUser(senderId) }.returns(MatrixUser(senderId))
                    coEvery { membershipServiceMock.getMembershipsSizeByRoomId(roomId) }.returns(2L)
                    cut.handleMessage(roomId, "dino", senderId, contextMock).shouldBeTrue()
                    coVerifyAll {
                        smsSendCommandHelperMock wasNot Called
                        smsInviteCommandHelperMock wasNot Called
                        contextMock.answer("help")
                    }
                }
            }
        }

        afterTest {
            clearMocks(
                    smsSendCommandHelperMock,
                    smsInviteCommandHelperMock,
                    phoneNumberServiceMock,
                    userServiceMock,
                    membershipServiceMock
            )
        }
    }
}