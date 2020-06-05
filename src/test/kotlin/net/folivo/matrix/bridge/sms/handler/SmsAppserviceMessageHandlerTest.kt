package net.folivo.matrix.bridge.sms.handler

import io.mockk.Called
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.room.AppserviceRoomRepository
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class SmsAppserviceMessageHandlerTest {

    @MockK
    lateinit var sendSmsServiceMock: SendSmsService

    @MockK
    lateinit var smsBotMessageHandlerMock: SmsBotMessageHandler

    @MockK
    lateinit var roomRepositoryMock: AppserviceRoomRepository

    @MockK
    lateinit var botPropertiesMock: MatrixBotProperties

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @InjectMockKs
    lateinit var cut: SmsAppserviceMessageHandler

    @BeforeEach
    fun beforeEach() {
        every { smsBridgePropertiesMock.defaultRoomId } returns "defaultRoomId"
        every { botPropertiesMock.serverName } returns "someServerName"
        every { botPropertiesMock.username } returns "smsbot"
        every {
            sendSmsServiceMock.sendSms(
                    any(),
                    any(),
                    any(),
                    content is TextMessageEventContent
            )
        } returns Mono.empty()
        every {
            smsBotMessageHandlerMock.handleMessageToSmsBot(
                    any(),
                    any(),
                    any(),
                    context::answer
            )
        } returns Mono.empty()
    }

    @Test
    fun `should delegate to SendSmsService when message seems to be outgoing SMS`() {
        val messageContext = mockk<MessageContext>(relaxed = true)
        every { messageContext.roomId } returns "someRoomId"
        every { messageContext.originalEvent.sender } returns "someSender"
        val roomMock1 = mockk<AppserviceRoom> {
            every { members } returns mutableMapOf(
                    mockk<AppserviceUser> {
                        every { userId } returns "someUserId"
                    } to MemberOfProperties(1)
            )
        }
        val roomMock2 = mockk<AppserviceRoom> {
            every { members } returns mutableMapOf(
                    mockk<AppserviceUser> {
                        every { userId } returns "@smsbot:someServerName"
                    } to MemberOfProperties(1),
                    mockk<AppserviceUser> {
                        every { userId } returns "@sms_1234567890:someServerName"
                    } to MemberOfProperties(1)
            )
        }
        every { roomRepositoryMock.findById("someRoomId") }.returnsMany(
                Mono.just(roomMock1), Mono.just(roomMock2)
        )

        StepVerifier
                .create(cut.handleMessage(TextMessageEventContent("someBody"), messageContext))
                .verifyComplete()

        verify { sendSmsServiceMock.sendSms(roomMock1, "someBody", "someSender", content is TextMessageEventContent) }

        // also try when more then one member
        StepVerifier
                .create(cut.handleMessage(TextMessageEventContent("someBody"), messageContext))
                .verifyComplete()

        verify { sendSmsServiceMock.sendSms(roomMock2, "someBody", "someSender", content is TextMessageEventContent) }
    }

    @Test
    fun `should delegate to SmsBotHandler when message seems to be for bot`() {
        val roomMock = mockk<AppserviceRoom> {
            every { members } returns mutableMapOf(
                    mockk<AppserviceUser> {
                        every { userId } returns "@smsbot:someServerName"
                    } to MemberOfProperties(1)
            )
        }
        every { roomRepositoryMock.findById("someRoomId") }.returns(
                Mono.just(roomMock)
        )

        val messageContext = mockk<MessageContext>(relaxed = true)
        every { messageContext.roomId } returns "someRoomId"
        every { messageContext.originalEvent.sender } returns "someSender"

        StepVerifier
                .create(cut.handleMessage(TextMessageEventContent("someBody"), messageContext))
                .verifyComplete()

        verify { smsBotMessageHandlerMock.handleMessageToSmsBot(roomMock, "someBody", "someSender", context::answer) }

    }

    @Test
    fun `should ignore messages to default room`() {
        val messageContext = mockk<MessageContext>(relaxed = true)
        every { messageContext.roomId } returns "defaultRoomId"
        every { messageContext.originalEvent.sender } returns "someSender"

        StepVerifier
                .create(cut.handleMessage(TextMessageEventContent("someBody"), messageContext))
                .verifyComplete()

        verify { sendSmsServiceMock wasNot Called }
    }
}