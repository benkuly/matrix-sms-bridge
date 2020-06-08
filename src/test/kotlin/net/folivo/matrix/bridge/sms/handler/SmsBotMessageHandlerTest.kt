package net.folivo.matrix.bridge.sms.handler

import io.mockk.Called
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class SmsBotMessageHandlerTest {
    @MockK
    lateinit var roomServiceMock: SmsMatrixAppserviceRoomService

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @InjectMockKs
    lateinit var cut: SmsBotMessageHandler

    @MockK
    lateinit var roomMock: AppserviceRoom

    @MockK
    lateinit var contextMock: MessageContext

    @BeforeEach
    fun beforeEach() {
        every { smsBridgePropertiesMock.templates.botTooManyMembers }.returns("tooMany")
        every { smsBridgePropertiesMock.templates.botHelp }.returns("help")
        every { contextMock.answer(any(), any()) }.returns(Mono.empty())
    }

    @Test
    fun `should run command`() {
        every { roomMock.members.size }.returns(2)
        every { roomServiceMock.createRoomAndSendMessage(any(), any(), any(), any(), any()) }
                .returns(Mono.just("message send"))
        every { smsBridgePropertiesMock.defaultRegion }.returns("DE")
        StepVerifier
                .create(
                        cut.handleMessageToSmsBot(
                                roomMock,
                                "sms send -t 01111111111 'some Text'",
                                "someSender",
                                contextMock
                        )
                )
                .verifyComplete()
        verify(exactly = 1) {
            contextMock.answer(match { it.body == "message send" })
        }
    }

    @Test
    fun `should answer with error when too many members for sms command`() {
        every { roomMock.members.size }.returns(3)
        StepVerifier
                .create(cut.handleMessageToSmsBot(roomMock, "sms bla", "someSender", contextMock))
                .verifyComplete()
        verify { contextMock.answer(match { it.body == "tooMany" }) }
    }

    @Test
    fun `should answer with help when not sms command`() {
        every { roomMock.members.size }.returns(2)
        StepVerifier
                .create(cut.handleMessageToSmsBot(roomMock, "bla", "someSender", contextMock))
                .verifyComplete()
        verify { contextMock.answer(match { it.body == "help" }) }
    }

    @Test
    fun `should do nothing`() {
        every { roomMock.members.size }.returns(3)
        StepVerifier
                .create(cut.handleMessageToSmsBot(roomMock, "bla", "someSender", contextMock))
                .verifyComplete()
        verify { contextMock wasNot Called }
    }
}