package net.folivo.matrix.bridge.sms.handler

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import net.folivo.matrix.bot.event.MessageContext
import net.folivo.matrix.core.model.MatrixId.EventId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class SmsBotConsoleTest {

    @MockK
    lateinit var contextMock: MessageContext

    @Test
    fun `should send text to matrix room`() {
        coEvery { contextMock.answer(any<String>(), any()) }.returns(EventId("event", "server"))
        val cut = SmsBotConsole(contextMock)
        cut.print("some Text", true)
        coVerify { contextMock.answer("some Text") }
    }

    @Test
    fun `line separator should be empty`() {
        val cut = SmsBotConsole(contextMock)
        assertThat(cut.lineSeparator).isEmpty()
    }
}