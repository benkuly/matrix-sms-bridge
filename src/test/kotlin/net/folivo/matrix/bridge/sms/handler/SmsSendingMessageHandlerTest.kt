package net.folivo.matrix.bridge.sms.handler

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.mapping.SendSmsService
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class SmsSendingMessageHandlerTest {

    @MockK
    lateinit var sendSmsServiceMock: SendSmsService

    @InjectMockKs
    lateinit var cut: SmsSendingMessageHandler

    @Test
    fun `should delegate messages to service`() {
        every { sendSmsServiceMock.sendSms(any(), any(), any()) } returns Mono.empty()

        val messageContext = mockk<MessageContext>(relaxed = true)
        every { messageContext.roomId } returns "someRoomId"
        every { messageContext.originalEvent.sender } returns "someSender"

        StepVerifier
                .create(cut.handleMessage(TextMessageEventContent("someBody"), messageContext))
                .verifyComplete()

        verify { sendSmsServiceMock.sendSms("someRoomId", "someBody", "someSender") }
    }
}