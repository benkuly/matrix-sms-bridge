package net.folivo.matrix.bridge.sms.handler

import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktConsole
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.verifyAll
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.ALWAYS
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.AUTO
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class SendSmsCommandTest {
    @MockK
    lateinit var helper: SendSmsCommandHelper

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    private lateinit var cut: SendSmsCommand

    @MockK(relaxed = true)
    lateinit var consoleMock: CliktConsole


    @BeforeEach
    fun beforeEach() {
        every { helper.createRoomAndSendMessage(any(), any(), any(), any(), any()) }
                .returns(Mono.just("answer"))
        every { smsBridgePropertiesMock.defaultRegion }.returns("DE")
        every { smsBridgePropertiesMock.templates.botSmsSendInvalidTelephoneNumber }.returns("invalid")
        cut = SendSmsCommand("someSender", helper, smsBridgePropertiesMock)
        cut.context { console = consoleMock }
    }

    @Test
    fun `should send message`() {
        cut.parse(listOf("some text", "-t", "017331111111", "-t", "017332222222"))

        verifyAll {
            helper.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    receiverNumbers = listOf("+4917331111111"),
                    roomName = null,
                    roomCreationMode = AUTO
            )
            helper.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    receiverNumbers = listOf("+4917332222222"),
                    roomName = null,
                    roomCreationMode = AUTO
            )
        }
    }

    @Test
    fun `should send message to group and use name`() {
        cut.parse(listOf("some text", "-t", "017331111111", "-t", "017332222222", "-n", "some name", "-g"))

        verify {
            helper.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    receiverNumbers = listOf("+4917331111111", "+4917332222222"),
                    roomName = "some name",
                    roomCreationMode = AUTO
            )
        }
    }

    @Test
    fun `should send message and create room`() {
        cut.parse(listOf("some text", "-t", "017331111111", "-m", "always"))

        verify {
            helper.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    receiverNumbers = listOf("+4917331111111"),
                    roomName = null,
                    roomCreationMode = ALWAYS
            )
        }
    }

    @Test
    fun `should echo answer from service`() {
        cut.parse(listOf("some text", "-t", "017331111111"))
        cut.parse(listOf("some text", "-t", "017331111111", "-g"))

        verify(exactly = 2) { consoleMock.print("answer", any()) }
    }

    @Test
    fun `should fail and echo when wrong telephone number`() {
        cut.parse(listOf("some text", "-t", "abc"))
        cut.parse(listOf("some text", "-t", "123456789123456789"))
        cut.parse(listOf("some text", "-t", "012345678 DINO"))

        verify(exactly = 3) { consoleMock.print("invalid", any()) }
    }
}