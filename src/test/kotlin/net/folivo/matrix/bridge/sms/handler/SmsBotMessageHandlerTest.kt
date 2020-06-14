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
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class SmsBotMessageHandlerTest {
    @MockK
    lateinit var sendSmsCommandHelperMock: SendSmsCommandHelper

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
        every { roomMock.members.size }.returns(2)
        every { roomMock.members.keys }.returns(
                mutableSetOf(
                        AppserviceUser("someUserId1", true),
                        AppserviceUser("someUserId2", false)
                )
        )
    }

    @Test
    fun `should run command`() {
        every { sendSmsCommandHelperMock.createRoomAndSendMessage(any(), any(), any(), any(), any()) }
                .returns(Mono.just("message send"))
        every { smsBridgePropertiesMock.defaultRegion }.returns("DE")
        StepVerifier
                .create(
                        cut.handleMessageToSmsBot(
                                roomMock,
                                "sms send -t 017392837462 'some Text'",
                                "someUserId2",
                                contextMock
                        )
                )
                .assertNext { assertThat(it).isTrue() }
                .verifyComplete()
        verify(exactly = 1) {
            contextMock.answer(match<NoticeMessageEventContent> { it.body == "message send" })
        }
    }

    @Test
    fun `should answer with error when too many members for sms command`() {
        every { roomMock.members.size }.returns(3)
        every { roomMock.members.keys }.returns(
                mutableSetOf(
                        AppserviceUser("someUserId1", true),
                        AppserviceUser("someUserId2", false),
                        AppserviceUser("someUserId3", false)
                )
        )
        StepVerifier
                .create(cut.handleMessageToSmsBot(roomMock, "sms bla", "someUserId2", contextMock))
                .assertNext { assertThat(it).isTrue() }
                .verifyComplete()
        verify { contextMock.answer(match<NoticeMessageEventContent> { it.body == "tooMany" }) }
    }

    @Test
    fun `should answer with help when not sms command`() {
        StepVerifier
                .create(cut.handleMessageToSmsBot(roomMock, "bla", "someUserId2", contextMock))
                .assertNext { assertThat(it).isTrue() }
                .verifyComplete()
        verify { contextMock.answer(match<NoticeMessageEventContent> { it.body == "help" }) }
    }

    @Test
    fun `should not allow managed user to run command`() {
        StepVerifier
                .create(cut.handleMessageToSmsBot(roomMock, "sms send", "someUserId1", contextMock))
                .assertNext { assertThat(it).isFalse() }
                .verifyComplete()
        StepVerifier
                .create(cut.handleMessageToSmsBot(roomMock, "sms send", "notKnownUserId", contextMock))
                .assertNext { assertThat(it).isFalse() }
                .verifyComplete()
        verify { contextMock wasNot Called }
    }

    @Test
    fun `should do nothing when all members are managed`() {
        every { roomMock.members.keys }.returns(
                mutableSetOf(
                        AppserviceUser("someUserId1", true),
                        AppserviceUser("someUserId2", true)
                )
        )
        StepVerifier
                .create(cut.handleMessageToSmsBot(roomMock, "bla", "someUserId1", contextMock))
                .assertNext { assertThat(it).isFalse() }
                .verifyComplete()
        verify { contextMock wasNot Called }
    }

    @Test
    fun `should do nothing when too many members`() {
        every { roomMock.members.size }.returns(3)
        every { roomMock.members.keys }.returns(
                mutableSetOf(
                        AppserviceUser("someUserId1", true),
                        AppserviceUser("someUserId2", false),
                        AppserviceUser("someUserId3", false)
                )
        )
        StepVerifier
                .create(cut.handleMessageToSmsBot(roomMock, "bla", "someUserId2", contextMock))
                .assertNext { assertThat(it).isFalse() }
                .verifyComplete()
        verify { contextMock wasNot Called }
    }

    @Test
    fun `should catch errors`() {
        StepVerifier
                .create(cut.handleMessageToSmsBot(roomMock, "sms send bla", "someUserId2", contextMock))
                .assertNext { assertThat(it).isTrue() }
                .verifyComplete()
        verify { contextMock.answer(match<NoticeMessageEventContent> { it.body.contains("Error") }) }
    }
}