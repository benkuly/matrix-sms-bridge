package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktConsole
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.verifyAll
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService.RoomCreationMode.ALWAYS
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService.RoomCreationMode.AUTO
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class SendSmsCommandTest {
    @MockK
    lateinit var roomServiceMock: SmsMatrixAppserviceRoomService

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    private lateinit var cut: SendSmsCommand

    @MockK(relaxed = true)
    lateinit var consoleMock: CliktConsole


    @BeforeEach
    fun beforeEach() {
        every { roomServiceMock.createRoomAndSendMessage(any(), any(), any(), any(), any()) }
                .returns(Mono.just("answer"))
        every { smsBridgePropertiesMock.defaultRegion }.returns("DE")
        every { smsBridgePropertiesMock.templates.botSmsSendInvalidTelephoneNumber }.returns("invalid")
        cut = SendSmsCommand("someSender", roomServiceMock, smsBridgePropertiesMock)
        cut.context { console = consoleMock }
    }

    @Test
    fun `should send message`() {
        cut.parse(listOf("some text", "-t", "01111111111", "-t", "02222222222"))

        verifyAll {
            roomServiceMock.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    receiverNumbers = listOf("491111111111"),
                    roomName = null,
                    roomCreationMode = AUTO
            )
            roomServiceMock.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    receiverNumbers = listOf("492222222222"),
                    roomName = null,
                    roomCreationMode = AUTO
            )
        }
    }

    @Test
    fun `should send message to group and use name`() {
        cut.parse(listOf("some text", "-t", "01111111111", "-t", "02222222222", "-n", "some name", "-g"))

        verify {
            roomServiceMock.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    receiverNumbers = listOf("491111111111", "492222222222"),
                    roomName = "some name",
                    roomCreationMode = AUTO
            )
        }
    }

    @Test
    fun `should send message and create room`() {
        cut.parse(listOf("some text", "-t", "01111111111", "-m", "always"))

        verify {
            roomServiceMock.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    receiverNumbers = listOf("491111111111"),
                    roomName = null,
                    roomCreationMode = ALWAYS
            )
        }
    }

    @Test
    fun `should echo answer from service`() {
        cut.parse(listOf("some text", "-t", "01111111111"))
        cut.parse(listOf("some text", "-t", "01111111111", "-g"))

        verify(exactly = 2) { consoleMock.print("answer", any()) }
    }

    @Test
    fun `should fail and echo when wrong telephone number`() {
        cut.parse(listOf("some text", "-t", "abc"))
        cut.parse(listOf("some text", "-t", "123456789123456789"))

        verify(exactly = 2) { consoleMock.print("invalid", any()) }
    }
}