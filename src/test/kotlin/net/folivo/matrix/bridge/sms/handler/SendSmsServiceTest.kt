package net.folivo.matrix.bridge.sms.handler

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.verifyAll
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
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

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
        every { smsProviderMock.sendSms(any(), any()) }.returns(Mono.empty())
        every { smsBridgePropertiesMock.templates.outgoingMessage }.returns("someTemplate")
        every { smsBridgePropertiesMock.templates.sendSmsError }.returns("sendSmsError")
        every { smsBridgePropertiesMock.templates.sendSmsIncompatibleMessage }.returns("incompatibleMessage")
        every { contextMock.answer(any(), any()) }.returns(Mono.just("someId"))
    }

    @Test
    fun `should send sms`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789:someServerName") to MemberOfProperties(1),
                        AppserviceUser("@sms_9876543210:someServerName") to MemberOfProperties(2)
                )
        )

        StepVerifier
                .create(cut.sendSms(room, "someBody", "someSender", contextMock, true))
                .verifyComplete()


        verifyAll {
            smsProviderMock.sendSms("+0123456789", "someTemplate")
            smsProviderMock.sendSms("+9876543210", "someTemplate")
        }
    }

    @Test
    fun `should not send sms back to sender (no loop)`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789:someServerName") to MemberOfProperties(1),
                        AppserviceUser("@sms_9876543210:someServerName") to MemberOfProperties(2)
                )
        )

        StepVerifier
                .create(
                        cut.sendSms(
                                room,
                                "someBody",
                                "@sms_0123456789:someServerName",
                                contextMock,
                                true
                        )
                )
                .verifyComplete()

        verify(exactly = 0) { smsProviderMock.sendSms("+0123456789", "someTemplate") }
        verify { smsProviderMock.sendSms("+9876543210", "someTemplate") }
    }

    @Test
    fun `should inject variables to template string`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789:someServerName") to MemberOfProperties(24)
                )
        )
        every { smsBridgePropertiesMock.templates.outgoingMessage }.returns("someTemplate {sender} {body} {token}")

        StepVerifier
                .create(
                        cut.sendSms(room, "someBody", "someSender", contextMock, true)
                )
                .verifyComplete()


        verifyAll {
            smsProviderMock.sendSms("+0123456789", "someTemplate someSender someBody #24")
        }
    }

    @Test
    fun `should use other template string when bot user`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789:someServerName") to MemberOfProperties(24)
                )
        )
        every { smsBridgePropertiesMock.templates.outgoingMessageFromBot }.returns("someBotTemplate {sender} {body} {token}")

        StepVerifier
                .create(
                        cut.sendSms(room, "someBody", "@someUsername:someServerName", contextMock, true)
                )
                .verifyComplete()

        verifyAll {
            smsProviderMock.sendSms("+0123456789", "someBotTemplate @someUsername:someServerName someBody #24")
        }
    }

    @Test
    fun `should ignore message to invalid telephone number (should never happen)`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789-24:someServerName") to MemberOfProperties(1),
                        AppserviceUser("@sms_9876543210:someServerName") to MemberOfProperties(2)
                )
        )

        StepVerifier
                .create(cut.sendSms(room, "someBody", "someSender", contextMock, true))
                .verifyComplete()

        verify(exactly = 0) { smsProviderMock.sendSms("+0123456789", "someTemplate") }
        verify { smsProviderMock.sendSms("+9876543210", "someTemplate") }
    }

    @Test
    fun `should answer with error message in room when something went wrong`() {
        val room = AppserviceRoom(
                "someRoomId",
                members = mutableMapOf(
                        AppserviceUser("@sms_0123456789:someServerName") to MemberOfProperties(1)
                )
        )
        every { smsProviderMock.sendSms("+0123456789", "someTemplate") }.returns(Mono.error(RuntimeException()))

        StepVerifier
                .create(cut.sendSms(room, "someBody", "someSender", contextMock, true))
                .verifyComplete()


        verifyAll {
            smsProviderMock.sendSms("+0123456789", "someTemplate")
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
                        AppserviceUser("@sms_0123456789:someServerName") to MemberOfProperties(1)
                )
        )
        every { smsProviderMock.sendSms(any(), any()) }.returns(Mono.empty())

        StepVerifier
                .create(cut.sendSms(room, "someBody", "someSender", contextMock, false))
                .verifyComplete()


        verifyAll {
            contextMock.answer(
                    match<NoticeMessageEventContent> { it.body == "incompatibleMessage" },
                    asUserId = "@sms_0123456789:someServerName"
            )
        }
    }
}