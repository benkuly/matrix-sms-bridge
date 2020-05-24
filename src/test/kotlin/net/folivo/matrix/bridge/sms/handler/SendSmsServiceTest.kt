package net.folivo.matrix.bridge.sms.handler

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.verifyAll
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.room.AppserviceRoomRepository
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class SendSmsServiceTest {

    @MockK
    lateinit var appserviceRoomRepositoryMock: AppserviceRoomRepository

    @MockK
    lateinit var roomServiceMock: SmsMatrixAppserviceRoomService

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @MockK
    lateinit var smsProviderMock: SmsProvider

    @MockK
    lateinit var matrixClientMock: MatrixClient

    @InjectMockKs
    lateinit var cut: SendSmsService

    @Test
    fun `should send sms`() {
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(
                Mono.just(
                        AppserviceRoom(
                                "someRoomId",
                                members = mutableMapOf(
                                        AppserviceUser("@sms_0123456789:someServerName") to MemberOfProperties(1),
                                        AppserviceUser("@sms_9876543210:someServerName") to MemberOfProperties(2)
                                )
                        )
                )
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
                                members = mutableMapOf(
                                        AppserviceUser("@sms_0123456789:someServerName") to MemberOfProperties(1),
                                        AppserviceUser("@sms_9876543210:someServerName") to MemberOfProperties(2)
                                )
                        )
                )
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
                                members = mutableMapOf(
                                        AppserviceUser("@sms_0123456789:someServerName") to MemberOfProperties(1),
                                        AppserviceUser("@sms_9876543210:someServerName") to MemberOfProperties(2)
                                )
                        )
                )
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
                                members = mutableMapOf(
                                        AppserviceUser("@sms_0123456789-24:someServerName") to MemberOfProperties(1),
                                        AppserviceUser("@sms_9876543210:someServerName") to MemberOfProperties(2)
                                )
                        )
                )
        )
        every { smsBridgePropertiesMock.templates.outgoingMessage }.returns("someTemplate")
        every { smsProviderMock.sendSms(any(), any()) }.returns(Mono.empty())

        StepVerifier
                .create(cut.sendSms("someRoomId", "someBody", "someSender"))
                .verifyComplete()

        verify(exactly = 0) { smsProviderMock.sendSms("+0123456789", "someTemplate") }
        verify { smsProviderMock.sendSms("+9876543210", "someTemplate") }
    }

    @Test
    fun `should answer with error message in room when something went wrong`() {
        every { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns(Mono.just("someId"))
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(
                Mono.just(
                        AppserviceRoom(
                                "someRoomId",
                                members = mutableMapOf(
                                        AppserviceUser("@sms_0123456789:someServerName") to MemberOfProperties(1)
                                )
                        )
                )
        )
        every { smsBridgePropertiesMock.templates.outgoingMessage }.returns("someTemplate")
        every { smsBridgePropertiesMock.templates.sendSmsError }.returns("sendSmsError")

        every { smsProviderMock.sendSms(any(), any()) }.returns(Mono.error(RuntimeException()))

        StepVerifier
                .create(cut.sendSms("someRoomId", "someBody", "someSender"))
                .verifyComplete()


        verifyAll {
            smsProviderMock.sendSms("+0123456789", "someTemplate")
            matrixClientMock.roomsApi.sendRoomEvent(
                    roomId = "someRoomId",
                    eventContent = match<NoticeMessageEventContent> { it.body == "sendSmsError" },
                    eventType = any(),
                    txnId = any(),
                    asUserId = "@sms_0123456789:someServerName"
            )
        }
    }
}