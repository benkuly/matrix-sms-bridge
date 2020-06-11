package net.folivo.matrix.bridge.sms.handler

import io.mockk.Called
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.user.SmsMatrixAppserviceUserService
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus.I_AM_A_TEAPOT
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class ReceiveSmsServiceTest {

    @MockK
    lateinit var matrixClientMock: MatrixClient

    @MockK
    lateinit var userServiceMock: SmsMatrixAppserviceUserService

    @MockK(relaxed = true)
    lateinit var matrixBotPropertiesMock: MatrixBotProperties

    @MockK(relaxed = true)
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @InjectMockKs
    lateinit var cut: ReceiveSmsService

    @BeforeEach
    fun configureProperties() {
        every { matrixBotPropertiesMock.serverName } returns "someServerName"
    }

    @Test
    fun `should receive with mapping token to matrix room`() {
        every { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns(Mono.just("someEventId"))
        every { userServiceMock.getRoomId("@sms_0123456789:someServerName", 123) }
                .returns(Mono.just("someRoomId"))
        StepVerifier
                .create(cut.receiveSms("#123someBody", "+0123456789"))
                .verifyComplete()

        verify {
            matrixClientMock.roomsApi.sendRoomEvent(
                    "someRoomId",
                    match<TextMessageEventContent> { it.body == "#123someBody" },
                    txnId = any(),
                    asUserId = "@sms_0123456789:someServerName"
            )
        }
    }

    @Test
    fun `should receive without valid mapping token to default matrix room, when given`() {
        every { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns(Mono.just("someEventId"))
        every { smsBridgePropertiesMock.defaultRoomId }.returns("defaultRoomId")
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithDefaultRoom }.returns("someMissingTokenWithDefaultRoom")
        every { userServiceMock.getRoomId("@sms_0123456789:someServerName", 123) }
                .returns(Mono.empty())
        every { smsBridgePropertiesMock.templates.defaultRoomIncomingMessage }.returns("{sender}: {body}")

        StepVerifier
                .create(cut.receiveSms("#123someBody", "+0123456789"))
                .expectNext("someMissingTokenWithDefaultRoom")
                .verifyComplete()

        verify {
            matrixClientMock.roomsApi.sendRoomEvent(
                    "defaultRoomId",
                    match<TextMessageEventContent> { it.body == "+0123456789: #123someBody" },
                    txnId = any()
            )
        }
    }

    @Test
    fun `should receive without mapping token to default matrix room, when given`() {
        every { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns(Mono.just("someEventId"))
        every { userServiceMock.getRoomId("@sms_0123456789:someServerName", null) }
                .returns(Mono.empty())
        every { smsBridgePropertiesMock.defaultRoomId }.returns("defaultRoomId")
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithDefaultRoom }.returns(null)
        every { smsBridgePropertiesMock.templates.defaultRoomIncomingMessage }.returns("{sender}: {body}")

        StepVerifier
                .create(cut.receiveSms("#someBody", "+0123456789"))
                .verifyComplete()

        verify {
            matrixClientMock.roomsApi.sendRoomEvent(
                    "defaultRoomId",
                    match<TextMessageEventContent> { it.body == "+0123456789: #someBody" },
                    txnId = any()
            )
        }
    }

    @Test
    fun `should ignore SMS without valid mapping token when no default matrix room is given`() {
        every { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns(Mono.just("someEventId"))
        every { smsBridgePropertiesMock.defaultRoomId }.returns(null)
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithoutDefaultRoom }.returns("someMissingTokenWithoutDefaultRoom")
        every { userServiceMock.getRoomId("@sms_0123456789:someServerName", 123) }
                .returns(Mono.empty())
        StepVerifier
                .create(cut.receiveSms("#123someBody", "+0123456789"))
                .expectNext("someMissingTokenWithoutDefaultRoom")
                .verifyComplete()

        verify { matrixClientMock wasNot Called }
    }

    @Test
    fun `should not answer when no template missingTokenWithDefaultRoom given`() {
        every { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns(Mono.just("someEventId"))
        every { userServiceMock.getRoomId("@sms_0123456789:someServerName", null) }
                .returns(Mono.empty())
        every { smsBridgePropertiesMock.defaultRoomId }.returns("defaultRoomId")
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithDefaultRoom }.returns(null)
        StepVerifier
                .create(cut.receiveSms("#someBody", "+0123456789"))
                .verifyComplete()
    }

    @Test
    fun `should not answer when empty template missingTokenWithDefaultRoom given`() {
        every { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns(Mono.just("someEventId"))
        every { userServiceMock.getRoomId("@sms_0123456789:someServerName", null) }
                .returns(Mono.empty())
        every { smsBridgePropertiesMock.defaultRoomId }.returns("defaultRoomId")
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithDefaultRoom }.returns("")
        StepVerifier
                .create(cut.receiveSms("#someBody", "+0123456789"))
                .verifyComplete()
    }

    @Test
    fun `should not answer when no template missingTokenWithoutDefaultRoom given`() {
        every { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns(Mono.just("someEventId"))
        every { userServiceMock.getRoomId("@sms_0123456789:someServerName", null) }
                .returns(Mono.empty())
        every { smsBridgePropertiesMock.defaultRoomId }.returns(null)
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithoutDefaultRoom }.returns(null)
        StepVerifier
                .create(cut.receiveSms("#someBody", "+0123456789"))
                .verifyComplete()
    }

    @Test
    fun `should have error, when telephone number was wrong`() {
        StepVerifier
                .create(cut.receiveSms("#someBody", "+0123456789 24"))
                .verifyError(MatrixServerException::class.java)
    }

    @Test
    fun `should have error, when message could not be send to matrix room`() {
        every { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns(Mono.error(MatrixServerException(I_AM_A_TEAPOT, ErrorResponse("TEA"))))
        every { userServiceMock.getRoomId("@sms_0123456789:someServerName", null) }
                .returns(Mono.empty())
        every { smsBridgePropertiesMock.defaultRoomId }.returns("someRoomId")
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithoutDefaultRoom }.returns(null)
        StepVerifier
                .create(cut.receiveSms("#someBody", "+0123456789"))
                .verifyError(MatrixServerException::class.java)
    }
}