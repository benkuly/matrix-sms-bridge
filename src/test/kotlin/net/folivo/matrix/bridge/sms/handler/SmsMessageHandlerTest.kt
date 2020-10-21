package net.folivo.matrix.bridge.sms.handler

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.mapping.MatrixSmsMapping
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.UnknownMessageEventContent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class SmsMessageHandlerTest {

    @MockK
    lateinit var messageToSmsHandlerMock: MessageToSmsHandler

    @MockK
    lateinit var messageToBotHandlerMock: MessageToBotHandler

    @MockK
    lateinit var roomServiceMock: SmsMatrixAppserviceRoomService

    @MockK
    lateinit var botPropertiesMock: MatrixBotProperties

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @InjectMockKs
    lateinit var cut: SmsMessageHandler

    @MockK
    lateinit var contextMock: MessageContext

    @MockK
    lateinit var roomMock: AppserviceRoom

    @BeforeEach
    fun beforeEach() {
        every { smsBridgePropertiesMock.defaultRoomId } returns "defaultRoomId"
        every { botPropertiesMock.serverName } returns "someServerName"
        every { botPropertiesMock.username } returns "smsbot"
        every { contextMock.roomId } returns "someRoomId"
        every { contextMock.originalEvent.sender } returns "someSender"
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }.returns(roomMock)

        coEvery { messageToSmsHandlerMock.handleMessage(any(), any(), any(), any(), any()) } just Runs
        coEvery { messageToBotHandlerMock.handleMessage(any(), any(), any(), any()) }.returns(false)
    }

    @Test
    fun `should delegate to SendSmsService when not message for sms bot`() {
        val roomMock1 = mockk<AppserviceRoom> {
            every { memberships } returns listOf(
                    Membership(AppserviceUser("someUserId", false), 1)
            )
        }
        val roomMock2 = mockk<AppserviceRoom> {
            every { memberships } returns listOf(
                    Membership(AppserviceUser("@smsbot:someServerName", true), 1),
                    Membership(AppserviceUser("@sms_1234567890:someServerName", true), 1)
            )
        }
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }.returnsMany(
                roomMock1, roomMock2
        )

        runBlocking { cut.handleMessage(TextMessageEventContent("someBody"), contextMock) }

        coVerify { messageToSmsHandlerMock.handleMessage(roomMock1, "someBody", "someSender", contextMock, true) }

        // also try when more then one member
        runBlocking { cut.handleMessage(TextMessageEventContent("someBody"), contextMock) }

        coVerify { messageToSmsHandlerMock.handleMessage(roomMock2, "someBody", "someSender", contextMock, true) }
    }

    @Test
    fun `should delegate to SendSmsService when message is not TextMessage`() {
        every { roomMock.memberships } returns listOf(
                MatrixSmsMapping(AppserviceUser("someUserId", false), 1)
        )

        runBlocking { cut.handleMessage(UnknownMessageEventContent("someBody", "image"), contextMock) }

        coVerify { messageToSmsHandlerMock.handleMessage(roomMock, "someBody", "someSender", contextMock, false) }
    }

    @Test
    fun `should not delegate to SendSmsService when message is NoticeMessage`() {
        every { roomMock.memberships } returns listOf(
                MatrixSmsMapping(AppserviceUser("someUserId", false), 1)
        )

        runBlocking { cut.handleMessage(NoticeMessageEventContent("someBody"), contextMock) }

        coVerify { messageToSmsHandlerMock wasNot Called }
    }

    @Test
    fun `should delegate to SmsBotHandler when room contains bot and not delegate to SendSmsService`() {
        coEvery { messageToBotHandlerMock.handleMessage(any(), any(), any(), any()) } returns true

        every { roomMock.memberships } returns listOf(
                MatrixSmsMapping(AppserviceUser("@smsbot:someServerName", true), 1)
        )

        runBlocking { cut.handleMessage(TextMessageEventContent("someBody"), contextMock) }

        coVerify { messageToBotHandlerMock.handleMessage(roomMock, "someBody", "someSender", contextMock) }
        coVerify { messageToSmsHandlerMock wasNot Called }
    }

    @Test
    fun `should not delegate to SmsBotHandler when room contains no bot`() {
        every { roomMock.memberships } returns listOf(
                MatrixSmsMapping(AppserviceUser("@someUser:someServerName", true), 1)
        )

        runBlocking { cut.handleMessage(TextMessageEventContent("someBody"), contextMock) }

        coVerify { messageToBotHandlerMock wasNot Called }
        coVerify { messageToSmsHandlerMock.handleMessage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should not delegate to SmsBotHandler when message is no text message`() {
        every { roomMock.memberships } returns listOf(
                MatrixSmsMapping(AppserviceUser("@someUser:someServerName", true), 1)
        )

        runBlocking { cut.handleMessage(NoticeMessageEventContent("someBody"), contextMock) }

        coVerify { messageToBotHandlerMock wasNot Called }
        coVerify { messageToSmsHandlerMock wasNot Called }
    }

    @Test
    fun `should ignore messages to default room`() {
        every { contextMock.roomId } returns "defaultRoomId"

        runBlocking { cut.handleMessage(TextMessageEventContent("someBody"), contextMock) }

        coVerify { messageToSmsHandlerMock wasNot Called }
    }
}