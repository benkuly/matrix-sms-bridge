package net.folivo.matrix.bridge.sms.handler

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class SendSmsServiceTest {

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @MockK
    lateinit var matrixBotPropertiesMock: MatrixBotProperties

    @MockK
    lateinit var smsProviderMock: SmsProvider

    @InjectMockKs
    lateinit var cut: SendSmsService

    @MockK
    lateinit var contextMock: MessageContext

    @BeforeEach
    fun beforeEach() {
        every { matrixBotPropertiesMock.serverName } returns "someServerName"
        every { matrixBotPropertiesMock.username } returns "someUsername"
        coEvery { smsProviderMock.sendSms(any(), any()) } just Runs
        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)
        every { smsBridgePropertiesMock.templates.outgoingMessageToken }.returns(" someToken")
        every { smsBridgePropertiesMock.templates.outgoingMessage }.returns("someTemplate")
        every { smsBridgePropertiesMock.templates.sendSmsError }.returns("sendSmsError")
        every { smsBridgePropertiesMock.templates.sendSmsIncompatibleMessage }.returns("incompatibleMessage")
        coEvery { contextMock.answer(any(), any()) }.returns("someId")
    }

    @Test
    fun `should send sms`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789:someServerName", true) to MemberOfProperties(1),
                        AppserviceUser("@sms_9876543210:someServerName", true) to MemberOfProperties(2)
                )
        )

        runBlocking { cut.sendSms(room, "someBody", "someSender", contextMock, true) }


        coVerifyAll {
            smsProviderMock.sendSms("+0123456789", "someTemplate someToken")
            smsProviderMock.sendSms("+9876543210", "someTemplate someToken")
        }
    }

    @Test
    fun `should not send sms back to sender (no loop)`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789:someServerName", true) to MemberOfProperties(1),
                        AppserviceUser("@sms_9876543210:someServerName", true) to MemberOfProperties(2)
                )
        )

        runBlocking {
            cut.sendSms(
                    room,
                    "someBody",
                    "@sms_0123456789:someServerName",
                    contextMock,
                    true
            )
        }

        coVerify(exactly = 0) { smsProviderMock.sendSms("+0123456789", any()) }
        coVerify { smsProviderMock.sendSms("+9876543210", "someTemplate someToken") }
    }

    @Test
    fun `should inject variables to template string`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789:someServerName", true) to MemberOfProperties(24)
                )
        )
        every { smsBridgePropertiesMock.templates.outgoingMessage }.returns("someTemplate {sender} {body} ")
        every { smsBridgePropertiesMock.templates.outgoingMessageToken }.returns("{token}")


        runBlocking {
            cut.sendSms(room, "someBody", "someSender", contextMock, true)
        }


        coVerify { smsProviderMock.sendSms("+0123456789", "someTemplate someSender someBody #24") }
    }

    @Test
    fun `should use other template string when bot user`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789:someServerName", true) to MemberOfProperties(24)
                )
        )
        every { smsBridgePropertiesMock.templates.outgoingMessageFromBot }.returns("someBotTemplate {sender} {body} ")
        every { smsBridgePropertiesMock.templates.outgoingMessageToken }.returns("{token}")

        runBlocking {
            cut.sendSms(room, "someBody", "@someUsername:someServerName", contextMock, true)
        }

        coVerify {
            smsProviderMock.sendSms("+0123456789", "someBotTemplate @someUsername:someServerName someBody #24")
        }
    }

    @Test
    fun `should use other template string when token not needed`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789:someServerName", true) to MemberOfProperties(24)
                )
        )
        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(true)
        every { smsBridgePropertiesMock.templates.outgoingMessageFromBot }.returns("someBotTemplate {sender} {body}")

        runBlocking {
            cut.sendSms(room, "someBody", "@someUsername:someServerName", contextMock, true)
        }

        coVerify {
            smsProviderMock.sendSms("+0123456789", "someBotTemplate @someUsername:someServerName someBody")
        }
    }

    @Test
    fun `should ignore message to invalid telephone number (should never happen)`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789-24:someServerName", true) to MemberOfProperties(1),
                        AppserviceUser("@sms_9876543210:someServerName", true) to MemberOfProperties(2)
                )
        )

        runBlocking { cut.sendSms(room, "someBody", "someSender", contextMock, true) }

        coVerify(exactly = 0) { smsProviderMock.sendSms("+0123456789", any()) }
        coVerify { smsProviderMock.sendSms("+9876543210", "someTemplate someToken") }
    }

    @Test
    fun `should answer with error message in room when something went wrong`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789:someServerName", true) to MemberOfProperties(1)
                )
        )
        coEvery { smsProviderMock.sendSms("+0123456789", any()) }.throws(RuntimeException())

        runBlocking { cut.sendSms(room, "someBody", "someSender", contextMock, true) }


        coVerifyAll {
            smsProviderMock.sendSms("+0123456789", "someTemplate someToken")
            contextMock.answer(
                    match<NoticeMessageEventContent> { it.body == "sendSmsError" },
                    asUserId = "@sms_0123456789:someServerName"
            )
        }
    }

    @Test
    fun `should answer with error message in room when unsupported message type`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789:someServerName", true) to MemberOfProperties(1)
                )
        )

        runBlocking { cut.sendSms(room, "someBody", "someSender", contextMock, false) }


        coVerify {
            contextMock.answer(
                    match<NoticeMessageEventContent> { it.body == "incompatibleMessage" },
                    asUserId = "@sms_0123456789:someServerName"
            )
        }
    }
}