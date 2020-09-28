package net.folivo.matrix.bridge.sms.handler

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.springframework.http.HttpStatus.I_AM_A_TEAPOT

@ExtendWith(MockKExtension::class)
class ReceiveSmsServiceTest {

    @MockK
    lateinit var matrixClientMock: MatrixClient

    @MockK
    lateinit var roomServiceMock: SmsMatrixAppserviceRoomService

    @MockK(relaxed = true)
    lateinit var matrixBotPropertiesMock: MatrixBotProperties

    @MockK(relaxed = true)
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @InjectMockKs
    lateinit var cut: ReceiveSmsService

    @BeforeEach
    fun configureProperties() {
        every { matrixBotPropertiesMock.serverName } returns "someServerName"
        coEvery { roomServiceMock.syncUserAndItsRooms(any()) } just Runs
    }

    @Test
    fun `should receive with mapping token to matrix room`() {
        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns("someEventId")
        coEvery { roomServiceMock.getRoom("@sms_0123456789:someServerName", 123) }
                .returns(AppserviceRoom("someRoomId"))

        val result = runBlocking { cut.receiveSms("#123someBody", "+0123456789") }
        assertThat(result).isNull()

        coVerify {
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
        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns("someEventId")
        every { smsBridgePropertiesMock.defaultRoomId }.returns("defaultRoomId")
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithDefaultRoom }.returns("someMissingTokenWithDefaultRoom")
        coEvery { roomServiceMock.getRoom("@sms_0123456789:someServerName", 123) }
                .returns(null)
        every { smsBridgePropertiesMock.templates.defaultRoomIncomingMessage }.returns("{sender}: {body}")

        val result = runBlocking { cut.receiveSms("#123someBody", "+0123456789") }
        assertThat(result).isEqualTo("someMissingTokenWithDefaultRoom")

        coVerify {
            matrixClientMock.roomsApi.sendRoomEvent(
                    "defaultRoomId",
                    match<TextMessageEventContent> { it.body == "+0123456789: #123someBody" },
                    txnId = any()
            )
        }
    }

    @Test
    fun `should receive without mapping token to default matrix room, when given`() {
        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns("someEventId")
        coEvery { roomServiceMock.getRoom("@sms_0123456789:someServerName", null) }
                .returns(null)
        every { smsBridgePropertiesMock.defaultRoomId }.returns("defaultRoomId")
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithDefaultRoom }.returns(null)
        every { smsBridgePropertiesMock.templates.defaultRoomIncomingMessage }.returns("{sender}: {body}")

        val result = runBlocking { cut.receiveSms("#someBody", "+0123456789") }
        assertThat(result).isNull()

        coVerify {
            matrixClientMock.roomsApi.sendRoomEvent(
                    "defaultRoomId",
                    match<TextMessageEventContent> { it.body == "+0123456789: #someBody" },
                    txnId = any()
            )
        }
    }

    @Test
    fun `should ignore SMS without valid mapping token when no default matrix room is given`() {
        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns("someEventId")
        every { smsBridgePropertiesMock.defaultRoomId }.returns(null)
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithoutDefaultRoom }.returns("someMissingTokenWithoutDefaultRoom")
        coEvery { roomServiceMock.getRoom("@sms_0123456789:someServerName", 123) }
                .returns(null)

        val result = runBlocking { cut.receiveSms("#123someBody", "+0123456789") }
        assertThat(result).isEqualTo("someMissingTokenWithoutDefaultRoom")

        coVerify { matrixClientMock wasNot Called }
    }

    @Test
    fun `should not answer when no template missingTokenWithDefaultRoom given`() {
        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns("someEventId")
        coEvery { roomServiceMock.getRoom("@sms_0123456789:someServerName", null) }
                .returns(null)
        every { smsBridgePropertiesMock.defaultRoomId }.returns("defaultRoomId")
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithDefaultRoom }.returns(null)

        val result = runBlocking { cut.receiveSms("#someBody", "+0123456789") }
        assertThat(result).isNull()
    }

    @Test
    fun `should not answer when empty template answerInvalidTokenWithDefaultRoom given`() {
        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns("someEventId")
        coEvery { roomServiceMock.getRoom("@sms_0123456789:someServerName", null) }
                .returns(null)
        every { smsBridgePropertiesMock.defaultRoomId }.returns("defaultRoomId")
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithDefaultRoom }.returns("")

        val result = runBlocking { cut.receiveSms("#someBody", "+0123456789") }
        assertThat(result).isNull()
    }

    @Test
    fun `should not answer when no template answerInvalidTokenWithoutDefaultRoom given`() {
        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns("someEventId")
        coEvery { roomServiceMock.getRoom("@sms_0123456789:someServerName", null) }
                .returns(null)
        every { smsBridgePropertiesMock.defaultRoomId }.returns(null)
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithoutDefaultRoom }.returns(null)

        val result = runBlocking { cut.receiveSms("#someBody", "+0123456789") }
        assertThat(result).isNull()
    }

    @Test
    fun `should have error, when telephone number was wrong`() {
        try {
            runBlocking { cut.receiveSms("#someBody", "+0123456789 24") }
            fail { "should have error" }
        } catch (error: MatrixServerException) {

        }
    }

    @Test
    fun `should have error, when message could not be send to matrix room`() {
        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .throws(MatrixServerException(I_AM_A_TEAPOT, ErrorResponse("TEA")))
        coEvery { roomServiceMock.getRoom("@sms_0123456789:someServerName", null) }
                .returns(null)
        every { smsBridgePropertiesMock.defaultRoomId }.returns("someRoomId")
        every { smsBridgePropertiesMock.templates.answerInvalidTokenWithoutDefaultRoom }.returns(null)

        try {
            runBlocking { cut.receiveSms("#someBody", "+0123456789") }
            fail { "should have error" }
        } catch (error: MatrixServerException) {

        }
    }
}