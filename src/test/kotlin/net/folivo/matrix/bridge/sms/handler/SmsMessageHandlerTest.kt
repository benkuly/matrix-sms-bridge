package net.folivo.matrix.bridge.sms.handler

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.event.MessageContext
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.core.model.MatrixId.RoomId
import net.folivo.matrix.core.model.MatrixId.UserId
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent

class SmsMessageHandlerTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val messageToSmsHandlerMock: MessageToSmsHandler = mockk()
        val messageToBotHandlerMock: MessageToBotHandler = mockk()
        val membershipServiceMock: MatrixMembershipService = mockk()
        val botPropertiesMock: MatrixBotProperties = mockk {
            every { botUserId } returns UserId("bot", "server")
        }
        val smsBridgePropertiesMock: SmsBridgeProperties = mockk {
            every { defaultRoomId } returns RoomId("default", "server")
        }

        val cut = SmsMessageHandler(
                messageToSmsHandlerMock,
                messageToBotHandlerMock,
                membershipServiceMock,
                botPropertiesMock,
                smsBridgePropertiesMock
        )

        val senderId = UserId("sender", "server")
        val roomId = RoomId("room", "server")
        val contextMock: MessageContext = mockk {
            every { originalEvent.sender }.returns(senderId)
        }

        beforeTest {
            coEvery { messageToSmsHandlerMock.handleMessage(any(), any(), any(), any(), any()) } just Runs
        }

        describe(SmsMessageHandler::handleMessage.name) {
            describe("room is default room") {
                beforeTest { every { contextMock.roomId }.returns(RoomId("default", "server")) }
                it("should ignore message") {
                    cut.handleMessage(TextMessageEventContent("body"), contextMock)
                    coVerify {
                        messageToBotHandlerMock wasNot Called
                        messageToSmsHandlerMock wasNot Called
                    }
                }
            }
            describe("message is notice") {
                beforeTest { every { contextMock.roomId }.returns(roomId) }
                it("should ignore message") {
                    cut.handleMessage(NoticeMessageEventContent("notice"), contextMock)
                    coVerify {
                        messageToBotHandlerMock wasNot Called
                        messageToSmsHandlerMock wasNot Called
                    }
                }
            }
            describe("massage is handleable") {
                beforeTest { every { contextMock.roomId }.returns(roomId) }
                describe("bot is member of room") {
                    beforeTest {
                        coEvery { membershipServiceMock.doesRoomContainsMembers(roomId, any()) }.returns(true)
                    }
                    it("should delegate to bot handler") {
                        coEvery { messageToBotHandlerMock.handleMessage(any(), any(), any(), any()) }.returns(true)
                        cut.handleMessage(TextMessageEventContent("body"), contextMock)
                        coVerify {
                            messageToBotHandlerMock.handleMessage(roomId, "body", senderId, contextMock)
                            messageToSmsHandlerMock wasNot Called
                        }
                    }
                    it("should delegate to sms handler when not handled by bot handler") {
                        coEvery { messageToBotHandlerMock.handleMessage(any(), any(), any(), any()) }.returns(false)
                        coEvery { messageToSmsHandlerMock.handleMessage(any(), any(), any(), any(), any()) } just Runs
                        cut.handleMessage(TextMessageEventContent("body"), contextMock)
                        coVerify {
                            messageToBotHandlerMock.handleMessage(roomId, "body", senderId, contextMock)
                            messageToSmsHandlerMock.handleMessage(roomId, "body", senderId, contextMock, true)
                        }
                    }
                }
                describe("bot is not member of room") {
                    beforeTest {
                        coEvery { membershipServiceMock.doesRoomContainsMembers(roomId, any()) }.returns(false)
                    }
                    it("should delegate to sms handler") {
                        coEvery { messageToSmsHandlerMock.handleMessage(any(), any(), any(), any(), any()) } just Runs
                        cut.handleMessage(TextMessageEventContent("body"), contextMock)
                        coVerify {
                            messageToBotHandlerMock wasNot Called
                            messageToSmsHandlerMock.handleMessage(roomId, "body", senderId, contextMock, true)
                        }
                    }
                    it("should detect if text message") {
                        coEvery { messageToSmsHandlerMock.handleMessage(any(), any(), any(), any(), any()) } just Runs
                        cut.handleMessage(mockk { every { body }.returns("body") }, contextMock)
                        coVerify {
                            messageToBotHandlerMock wasNot Called
                            messageToSmsHandlerMock.handleMessage(roomId, "body", senderId, contextMock, false)
                        }
                    }
                }
            }
        }

        afterTest {
            clearMocks(
                    messageToSmsHandlerMock,
                    messageToBotHandlerMock,
                    membershipServiceMock
            )
        }
    }
}