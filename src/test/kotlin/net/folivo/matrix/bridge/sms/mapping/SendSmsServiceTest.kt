package net.folivo.matrix.bridge.sms.mapping

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import net.folivo.matrix.bot.appservice.room.AppserviceRoom
import net.folivo.matrix.bot.appservice.room.AppserviceRoomRepository
import net.folivo.matrix.bot.appservice.user.AppserviceUser
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class SendSmsServiceTest {

    @MockK
    lateinit var appserviceRoomRepositoryMock: AppserviceRoomRepository

    @MockK
    lateinit var smsRoomServiceMock: SmsRoomService

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @MockK
    lateinit var smsProviderMock: SmsProvider

    @InjectMockKs
    lateinit var cut: SendSmsService

    @Test
    fun `should send sms`() {
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(
                Mono.just(
                        AppserviceRoom(
                                "someRoomId",
                                members = mutableSetOf(
                                        AppserviceUser("@sms_0123456789:someServerName"),
                                        AppserviceUser("@sms_9876543210:someServerName")
                                )
                        )
                )
        )
        every { smsRoomServiceMock.getBridgedSmsRoom(any(), any()) }.returnsMany(
                Mono.just(mockk<SmsRoom> { every { mappingToken } returns 1 }),
                Mono.just(mockk<SmsRoom> { every { mappingToken } returns 2 })
        )
        every { smsBridgePropertiesMock.templates.outgoingMessage }.returns("someTemplate")
        every { smsProviderMock.sendSms(any(), any()) }.returns(Mono.empty())

        StepVerifier
                .create(cut.sendSms("someRoomId", "someBody", "someSender"))
                .verifyComplete()


        verifyAll {
            smsProviderMock.sendSms("+0123456789", "someTemplate")
            smsProviderMock.sendSms("+9876543210", "someTemplate")
        }
    }

    @Test
    fun `should not send sms back to sender (no loop)`() {
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(
                Mono.just(
                        AppserviceRoom(
                                "someRoomId",
                                members = mutableSetOf(
                                        AppserviceUser("@sms_0123456789:someServerName"),
                                        AppserviceUser("@sms_9876543210:someServerName")
                                )
                        )
                )
        )
        every { smsRoomServiceMock.getBridgedSmsRoom(any(), any()) }.returnsMany(
                Mono.just(mockk<SmsRoom> { every { mappingToken } returns 1 }),
                Mono.just(mockk<SmsRoom> { every { mappingToken } returns 2 })
        )
        every { smsBridgePropertiesMock.templates.outgoingMessage }.returns("someTemplate")
        every { smsProviderMock.sendSms(any(), any()) }.returns(Mono.empty())

        StepVerifier
                .create(cut.sendSms("someRoomId", "someBody", "@sms_0123456789:someServerName"))
                .verifyComplete()

        verify(exactly = 0) { smsProviderMock.sendSms("+0123456789", "someTemplate") }
        verify { smsProviderMock.sendSms("+9876543210", "someTemplate") }
    }

    @Test
    fun `should inject variables to template string`() {
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(
                Mono.just(
                        AppserviceRoom(
                                "someRoomId",
                                members = mutableSetOf(
                                        AppserviceUser("@sms_0123456789:someServerName"),
                                        AppserviceUser("@sms_9876543210:someServerName")
                                )
                        )
                )
        )
        every { smsRoomServiceMock.getBridgedSmsRoom(any(), any()) }.returnsMany(
                Mono.just(mockk<SmsRoom> { every { mappingToken } returns 1 }),
                Mono.just(mockk<SmsRoom> { every { mappingToken } returns 2 })
        )
        every { smsBridgePropertiesMock.templates.outgoingMessage }.returns("someTemplate {sender} {body} {token}")
        every { smsProviderMock.sendSms(any(), any()) }.returns(Mono.empty())

        StepVerifier
                .create(cut.sendSms("someRoomId", "someBody", "someSender"))
                .verifyComplete()


        verifyAll {
            smsProviderMock.sendSms("+0123456789", "someTemplate someSender someBody #1")
            smsProviderMock.sendSms("+9876543210", "someTemplate someSender someBody #2")
        }
    }

    @Test
    fun `should ignore message to invalid telephone number (should never happen)`() {
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(
                Mono.just(
                        AppserviceRoom(
                                "someRoomId",
                                members = mutableSetOf(
                                        AppserviceUser("@sms_0123456789-24:someServerName"),
                                        AppserviceUser("@sms_9876543210:someServerName")
                                )
                        )
                )
        )
        every { smsRoomServiceMock.getBridgedSmsRoom(any(), any()) }.returnsMany(
                Mono.just(mockk<SmsRoom> { every { mappingToken } returns 1 }),
                Mono.just(mockk<SmsRoom> { every { mappingToken } returns 2 })
        )
        every { smsBridgePropertiesMock.templates.outgoingMessage }.returns("someTemplate")
        every { smsProviderMock.sendSms(any(), any()) }.returns(Mono.empty())

        StepVerifier
                .create(cut.sendSms("someRoomId", "someBody", "someSender"))
                .verifyComplete()

        verify(exactly = 0) { smsProviderMock.sendSms("+0123456789", "someTemplate") }
        verify { smsProviderMock.sendSms("+9876543210", "someTemplate") }
    }
}