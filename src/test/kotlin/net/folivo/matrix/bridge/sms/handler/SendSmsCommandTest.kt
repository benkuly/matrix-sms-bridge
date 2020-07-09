package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktConsole
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.NumberParseException.ErrorType.NOT_A_NUMBER
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.ALWAYS
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.AUTO
import net.folivo.matrix.bridge.sms.provider.PhoneNumberService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class SendSmsCommandTest {
    @MockK
    lateinit var helper: SendSmsCommandHelper

    @MockK
    lateinit var phoneNumberServiceMock: PhoneNumberService

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    private lateinit var cut: SendSmsCommand

    @MockK(relaxed = true)
    lateinit var consoleMock: CliktConsole


    @BeforeEach
    fun beforeEach() {
        coEvery { helper.createRoomAndSendMessage(any(), any(), any(), any(), any(), any()) }.returns("answer")
        every { smsBridgePropertiesMock.templates.botSmsSendInvalidTelephoneNumber }.returns("invalid")
        every { phoneNumberServiceMock.parseToInternationalNumber("017331111111") }.returns("+4917331111111")
        every { phoneNumberServiceMock.parseToInternationalNumber("017332222222") }.returns("+4917332222222")
        cut = SendSmsCommand("someSender", helper, phoneNumberServiceMock, smsBridgePropertiesMock)
        cut.context { console = consoleMock }
    }

    @Test
    fun `should send message to muliple numbers`() {
        cut.parse(listOf("some text", "-t", "017331111111", "-t", "017332222222"))

        coVerifyAll {
            helper.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    receiverNumbers = listOf("+4917331111111"),
                    roomName = null,
                    roomCreationMode = AUTO,
                    sendAfterLocal = null
            )
            helper.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    receiverNumbers = listOf("+4917332222222"),
                    roomName = null,
                    roomCreationMode = AUTO,
                    sendAfterLocal = null
            )
        }
    }

    @Test
    fun `should send message in future`() {
        cut.parse(listOf("some text", "-t", "017331111111", "-a", "1955-11-09T12:00"))

        coVerifyAll {
            helper.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    receiverNumbers = listOf("+4917331111111"),
                    roomName = null,
                    roomCreationMode = AUTO,
                    sendAfterLocal = LocalDateTime.of(1955, 11, 9, 12, 0)
            )
        }
    }

    @Test
    fun `should send message to group and use name`() {
        cut.parse(listOf("some text", "-t", "017331111111", "-t", "017332222222", "-n", "some name", "-g"))

        coVerify {
            helper.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    receiverNumbers = listOf("+4917331111111", "+4917332222222"),
                    roomName = "some name",
                    roomCreationMode = AUTO,
                    sendAfterLocal = null
            )
        }
    }

    @Test
    fun `should send message and create room`() {
        cut.parse(listOf("some text", "-t", "017331111111", "-m", "always"))

        coVerify {
            helper.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    receiverNumbers = listOf("+4917331111111"),
                    roomName = null,
                    roomCreationMode = ALWAYS,
                    sendAfterLocal = null
            )
        }
    }

    @Test
    fun `should echo answer from service`() {
        cut.parse(listOf("some text", "-t", "017331111111"))
        cut.parse(listOf("some text", "-t", "017331111111", "-g"))

        coVerify(exactly = 2) { consoleMock.print("answer", any()) }
    }

    @Test
    fun `should fail and echo when wrong telephone number`() {
        every { phoneNumberServiceMock.parseToInternationalNumber(any()) }.throws(
                NumberParseException(
                        NOT_A_NUMBER,
                        "not a valid number"
                )
        )
        cut.parse(listOf("some text", "-t", "012345678 DINO"))

        coVerify { consoleMock.print("invalid", any()) }
    }
}