package net.folivo.matrix.bridge.sms.handler

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class SmsBotConsoleTest {

    @MockK
    lateinit var contextMock: MessageContext

    @Test
    fun `should send text to matrix room`() {
        every { contextMock.answer(any(), any()) }.returns(Mono.empty())
        val cut = SmsBotConsole(contextMock)
        cut.print("some Text", true)
        verify { contextMock.answer(match<NoticeMessageEventContent> { it.body == "some Text" }) }
    }

    @Test
    fun `line separator should be empty`() {
        val cut = SmsBotConsole(contextMock)
        assertThat(cut.lineSeparator).isEmpty()
    }
}