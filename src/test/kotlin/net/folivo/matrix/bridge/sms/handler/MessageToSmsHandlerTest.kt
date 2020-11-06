package net.folivo.matrix.bridge.sms.handler

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.event.MessageContext
import net.folivo.matrix.bot.room.MatrixRoom
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bot.user.MatrixUser
import net.folivo.matrix.bot.user.MatrixUserService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.mapping.MatrixSmsMapping
import net.folivo.matrix.bridge.sms.mapping.MatrixSmsMappingService
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.core.model.MatrixId.*

class MessageToSmsHandlerTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val botUserId = UserId("bot", "server")
        val botPropertiesMock: MatrixBotProperties = mockk {
            every { botUserId }.returns(botUserId)
        }
        val smsBridgePropertiesMock: SmsBridgeProperties = mockk {
            every { templates.outgoingMessageToken }.returns(" with #{token}")
            every { templates.outgoingMessage }.returns("{sender} wrote {body}")
            every { templates.outgoingMessageFromBot }.returns("bot wrote {body}")
            every { templates.sendSmsError }.returns("error")
            every { templates.sendSmsIncompatibleMessage }.returns("incompatible")
            every { allowMappingWithoutToken }.returns(false)
        }
        val smsProviderMock: SmsProvider = mockk(relaxed = true)
        val roomServiceMock: MatrixRoomService = mockk()
        val userServiceMock: MatrixUserService = mockk()
        val mappingServiceMock: MatrixSmsMappingService = mockk()

        val cut = MessageToSmsHandler(
                botPropertiesMock,
                smsBridgePropertiesMock,
                smsProviderMock,
                roomServiceMock,
                userServiceMock,
                mappingServiceMock
        )

        val contextMock: MessageContext = mockk {
            coEvery { answer(any<String>(), any()) }.returns(EventId("event", "server"))
        }
        val roomId = RoomId("room", "server")
        val senderId = UserId("sender", "server")

        describe(MessageToSmsHandler::handleMessage.name) {
            val userId1 = UserId("sms_11111", "server")
            val userId2 = UserId("sms_22222", "server")
            beforeTest {
                coEvery { userServiceMock.getUsersByRoom(roomId) }.returns(
                        flowOf(
                                MatrixUser(botUserId, true),
                                MatrixUser(senderId, true),
                                MatrixUser(userId1),
                                MatrixUser(userId2)
                        )
                )
                coEvery { mappingServiceMock.getOrCreateMapping(userId1, roomId) }
                        .returns(MatrixSmsMapping("memId", 2))
                coEvery { mappingServiceMock.getOrCreateMapping(userId2, roomId) }
                        .returns(MatrixSmsMapping("memId", 3))
            }
            describe("mapping without token is allowed") {
                beforeTest {
                    every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(true)
                }
                it("should ignore token when room is managed") {
                    coEvery { roomServiceMock.getOrCreateRoom(roomId) }
                            .returns(MatrixRoom(roomId, true))
                    cut.handleMessage(roomId, "body", senderId, contextMock, true)
                    coVerify {
                        smsProviderMock.sendSms("+11111", "+11111 wrote body")
                        smsProviderMock.sendSms("+22222", "+22222 wrote body")
                    }
                }
                it("should ignore token when room is the only room") {
                    coEvery { roomServiceMock.getOrCreateRoom(roomId) }
                            .returns(MatrixRoom(roomId, false))
                    coEvery { roomServiceMock.getRoomsByMembers(any()) }
                            .returns(flowOf(mockk()))
                    cut.handleMessage(roomId, "body", senderId, contextMock, true)
                    coVerifyAll {
                        smsProviderMock.sendSms("+11111", "+11111 wrote body")
                        smsProviderMock.sendSms("+22222", "+22222 wrote body")
                    }
                }
                it("should not ignore token when room is not managed and not the only room") {
                    coEvery { roomServiceMock.getOrCreateRoom(roomId) }
                            .returns(MatrixRoom(roomId, false))
                    coEvery { roomServiceMock.getRoomsByMembers(any()) }
                            .returnsMany(flowOf(mockk(), mockk()), flowOf(mockk()))
                    coEvery { mappingServiceMock.getOrCreateMapping(any(), roomId).mappingToken }
                            .returns(2)
                    cut.handleMessage(roomId, "body", senderId, contextMock, true)
                    coVerifyAll {
                        smsProviderMock.sendSms("+11111", "+11111 wrote body with #2")
                        smsProviderMock.sendSms("+22222", "+22222 wrote body")
                    }
                }
            }
            describe("mapping without token is not allowed") {
                beforeTest {
                    every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)
                    coEvery { roomServiceMock.getOrCreateRoom(roomId) }
                            .returns(MatrixRoom(roomId, false))
                    coEvery { roomServiceMock.getRoomsByMembers(any()) }
                            .returns(flowOf(mockk(), mockk()))
                    coEvery { mappingServiceMock.getOrCreateMapping(any(), roomId).mappingToken }
                            .returns(2)
                }
                it("should send sms") {
                    cut.handleMessage(roomId, "body", senderId, contextMock, true)
                    coVerifyAll {
                        smsProviderMock.sendSms("+11111", "+11111 wrote body with #2")
                        smsProviderMock.sendSms("+22222", "+22222 wrote body with #2")
                    }
                }
                it("should not send sms back to sender (no loop)") {
                    cut.handleMessage(roomId, "body", userId1, contextMock, true)
                    coVerifyAll {
                        smsProviderMock.sendSms("+22222", "+22222 wrote body with #2")
                    }
                }
                it("should use other string with bot user") {
                    cut.handleMessage(roomId, "body", botUserId, contextMock, true)
                    coVerify {
                        smsProviderMock.sendSms("+11111", "bot wrote body with #2")
                        smsProviderMock.sendSms("+22222", "bot wrote body with #2")
                    }
                }
            }
            describe("should answer with error message") {
                beforeTest {
                    every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(true)
                    coEvery { roomServiceMock.getOrCreateRoom(roomId) }
                            .returns(MatrixRoom(roomId, true))
                }
                it("when send sms fails") {
                    coEvery { smsProviderMock.sendSms("+11111", any()) }.throws(RuntimeException())
                    cut.handleMessage(roomId, "body", senderId, contextMock, true)
                    coVerify {
                        smsProviderMock wasNot Called
                        contextMock.answer("error", asUserId = userId1)
                    }
                }
                it("when wrong message type") {
                    cut.handleMessage(roomId, "body", senderId, contextMock, false)
                    coVerify {
                        smsProviderMock wasNot Called
                        contextMock.answer("incompatible", asUserId = userId1)
                        contextMock.answer("incompatible", asUserId = userId2)
                    }
                }
            }
        }
        afterTest {
            clearMocks(
                    roomServiceMock,
                    userServiceMock,
                    mappingServiceMock
            )
        }
    }
}