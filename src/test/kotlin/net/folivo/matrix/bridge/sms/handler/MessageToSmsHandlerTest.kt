package net.folivo.matrix.bridge.sms.handler

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bot.user.MatrixUserService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.mapping.MatrixSmsMappingService
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.core.model.MatrixId.UserId

class MessageToSmsHandlerTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val botPropertiesMock: MatrixBotProperties = mockk {
            every { botUserId }.returns(UserId("bot", "server"))
        }
        val smsBridgePropertiesMock: SmsBridgeProperties = mockk()
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
    }
}

//    @BeforeEach
//    fun beforeEach() {
//        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)
//        every { smsBridgePropertiesMock.templates.outgoingMessageToken }.returns(" someToken")
//        every { smsBridgePropertiesMock.templates.outgoingMessage }.returns("someTemplate")
//        every { smsBridgePropertiesMock.templates.sendSmsError }.returns("sendSmsError")
//        every { smsBridgePropertiesMock.templates.sendSmsIncompatibleMessage }.returns("incompatibleMessage")
//        coEvery { contextMock.answer(any(), any()) }.returns("someId")
//        coEvery { roomServiceMock.getRooms(any()) }.returns(
//                flowOf(AppserviceRoom("someRoomId1"), AppserviceRoom("someRoomId2"))
//        )
//    }
//
//    @Test
//    fun `should send sms`() {
//        val room = AppserviceRoom(
//                "someRoomId",
//                memberships = listOf(
//                        MatrixSmsMapping(AppserviceUser("@sms_0123456789:someServerName", true), 1),
//                        MatrixSmsMapping(AppserviceUser("@sms_9876543210:someServerName", true), 2)
//                )
//        )
//
//        runBlocking { cut.handleMessage(room, "someBody", "someSender", contextMock, true) }
//
//
//        coVerifyAll {
//            smsProviderMock.sendSms("+0123456789", "someTemplate someToken")
//            smsProviderMock.sendSms("+9876543210", "someTemplate someToken")
//        }
//    }
//
//    @Test
//    fun `should not send sms back to sender (no loop)`() {
//        val room = AppserviceRoom(
//                "someRoomId",
//                memberships = listOf(
//                        MatrixSmsMapping(AppserviceUser("@sms_0123456789:someServerName", true), 1),
//                        MatrixSmsMapping(AppserviceUser("@sms_9876543210:someServerName", true), 2)
//                )
//        )
//
//        runBlocking {
//            cut.handleMessage(
//                    room,
//                    "someBody",
//                    "@sms_0123456789:someServerName",
//                    contextMock,
//                    true
//            )
//        }
//
//        coVerify(exactly = 0) { smsProviderMock.sendSms("+0123456789", any()) }
//        coVerify { smsProviderMock.sendSms("+9876543210", "someTemplate someToken") }
//    }
//
//    @Test
//    fun `should inject variables to template string`() {
//        val room = AppserviceRoom(
//                "someRoomId",
//                memberships = listOf(
//                        MatrixSmsMapping(AppserviceUser("@sms_0123456789:someServerName", true), 24)
//                )
//        )
//        every { smsBridgePropertiesMock.templates.outgoingMessage }.returns("someTemplate {sender} {body} ")
//        every { smsBridgePropertiesMock.templates.outgoingMessageToken }.returns("{token}")
//
//
//        runBlocking {
//            cut.handleMessage(room, "someBody", "someSender", contextMock, true)
//        }
//
//
//        coVerify { smsProviderMock.sendSms("+0123456789", "someTemplate someSender someBody #24") }
//    }
//
//    @Test
//    fun `should use other template string when bot user`() {
//        val room = AppserviceRoom(
//                "someRoomId",
//                memberships = listOf(
//                        MatrixSmsMapping(AppserviceUser("@sms_0123456789:someServerName", true), 24)
//                )
//        )
//        every { smsBridgePropertiesMock.templates.outgoingMessageFromBot }.returns("someBotTemplate {sender} {body} ")
//        every { smsBridgePropertiesMock.templates.outgoingMessageToken }.returns("{token}")
//
//        runBlocking {
//            cut.handleMessage(room, "someBody", "@someUsername:someServerName", contextMock, true)
//        }
//
//        coVerify {
//            smsProviderMock.sendSms("+0123456789", "someBotTemplate @someUsername:someServerName someBody #24")
//        }
//    }
//
//    @Test
//    fun `should use other template string when token not needed`() {
//        val room = AppserviceRoom(
//                "someRoomId",
//                memberships = listOf(
//                        MatrixSmsMapping(AppserviceUser("@sms_0123456789:someServerName", true), 24)
//                )
//        )
//        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(true)
//        every { smsBridgePropertiesMock.templates.outgoingMessageFromBot }.returns("someBotTemplate {sender} {body}")
//        coEvery { roomServiceMock.getRooms("@sms_0123456789:someServerName") }.returns(
//                flowOf(room)
//        )
//
//        runBlocking {
//            cut.handleMessage(room, "someBody", "@someUsername:someServerName", contextMock, true)
//        }
//
//        coVerify {
//            smsProviderMock.sendSms("+0123456789", "someBotTemplate @someUsername:someServerName someBody")
//        }
//    }
//
//    @Test
//    fun `should ignore message to invalid telephone number (should never happen)`() {
//        val room = AppserviceRoom(
//                "someRoomId",
//                memberships = listOf(
//                        MatrixSmsMapping(AppserviceUser("@sms_0123456789-24:someServerName", true), 1),
//                        MatrixSmsMapping(AppserviceUser("@sms_9876543210:someServerName", true), 2)
//                )
//        )
//
//        runBlocking { cut.handleMessage(room, "someBody", "someSender", contextMock, true) }
//
//        coVerify(exactly = 0) { smsProviderMock.sendSms("+0123456789", any()) }
//        coVerify { smsProviderMock.sendSms("+9876543210", "someTemplate someToken") }
//    }
//
//    @Test
//    fun `should answer with error message in room when something went wrong`() {
//        val room = AppserviceRoom(
//                "someRoomId",
//                memberships = listOf(
//                        MatrixSmsMapping(AppserviceUser("@sms_0123456789:someServerName", true), 1)
//                )
//        )
//        coEvery { smsProviderMock.sendSms("+0123456789", any()) }.throws(RuntimeException())
//
//        runBlocking { cut.handleMessage(room, "someBody", "someSender", contextMock, true) }
//
//
//        coVerifyAll {
//            smsProviderMock.sendSms("+0123456789", "someTemplate someToken")
//            contextMock.answer(
//                    match<NoticeMessageEventContent> { it.body == "sendSmsError" },
//                    asUserId = "@sms_0123456789:someServerName"
//            )
//        }
//    }
//
//    @Test
//    fun `should answer with error message in room when unsupported message type`() {
//        val room = AppserviceRoom(
//                "someRoomId",
//                memberships = listOf(
//                        MatrixSmsMapping(AppserviceUser("@sms_0123456789:someServerName", true), 1)
//                )
//        )
//
//        runBlocking { cut.handleMessage(room, "someBody", "someSender", contextMock, false) }
//
//
//        coVerify {
//            contextMock.answer(
//                    match<NoticeMessageEventContent> { it.body == "incompatibleMessage" },
//                    asUserId = "@sms_0123456789:someServerName"
//            )
//        }
//    }
//}