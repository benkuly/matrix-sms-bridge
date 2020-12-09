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
import net.folivo.matrix.bridge.sms.handler.SmsSendCommand.RoomCreationMode.ALWAYS
import net.folivo.matrix.bridge.sms.handler.SmsSendCommand.RoomCreationMode.AUTO
import net.folivo.matrix.bridge.sms.provider.PhoneNumberService
import net.folivo.matrix.core.model.MatrixId.UserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class SmsSendCommandTest {
    @MockK
    lateinit var handler: SmsSendCommandHandler

    @MockK
    lateinit var phoneNumberServiceMock: PhoneNumberService

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    private lateinit var cut: SmsSendCommand

    @MockK(relaxed = true)
    lateinit var consoleMock: CliktConsole

    private val senderId = UserId("sender", "server")


    @BeforeEach
    fun beforeEach() {
        coEvery { handler.handleCommand(any(), any(), any(), any(), any(), any(), any()) }.returns("answer")
        every { smsBridgePropertiesMock.templates.botSmsSendInvalidTelephoneNumber }.returns("invalid")
        every { phoneNumberServiceMock.parseToInternationalNumber("017331111111") }.returns("+4917331111111")
        every { phoneNumberServiceMock.parseToInternationalNumber("017332222222") }.returns("+4917332222222")
        cut = SmsSendCommand(senderId, handler, phoneNumberServiceMock, smsBridgePropertiesMock)
        cut.context { console = consoleMock }
    }

    @Test
    fun `should send message to muliple numbers`() {
        cut.parse(listOf("some text", "-t", "017331111111", "-t", "017332222222"))

        coVerifyAll {
            handler.handleCommand(
                body = "some text",
                senderId = senderId,
                receiverNumbers = setOf("+4917331111111"),
                inviteUserIds = setOf(),
                roomName = null,
                roomCreationMode = AUTO,
                sendAfterLocal = null
            )
            handler.handleCommand(
                body = "some text",
                senderId = senderId,
                receiverNumbers = setOf("+4917332222222"),
                inviteUserIds = setOf(),
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
            handler.handleCommand(
                body = "some text",
                senderId = senderId,
                receiverNumbers = setOf("+4917331111111"),
                inviteUserIds = setOf(),
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
            handler.handleCommand(
                body = "some text",
                senderId = senderId,
                receiverNumbers = setOf("+4917331111111", "+4917332222222"),
                inviteUserIds = setOf(),
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
            handler.handleCommand(
                body = "some text",
                senderId = senderId,
                receiverNumbers = setOf("+4917331111111"),
                inviteUserIds = setOf(),
                roomName = null,
                roomCreationMode = ALWAYS,
                sendAfterLocal = null
            )
        }
    }

    @Test
    fun `should invite users`() {
        cut.parse(listOf("some text", "-t", "017331111111", "-i", "@test1:test", "-i", "@test2:test"))

        coVerify {
            handler.handleCommand(
                body = "some text",
                senderId = senderId,
                receiverNumbers = setOf("+4917331111111"),
                inviteUserIds = setOf(UserId("test1", "test"), UserId("test2", "test")),
                roomName = null,
                roomCreationMode = AUTO,
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