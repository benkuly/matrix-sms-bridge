package net.folivo.matrix.bridge.sms.handler

import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.provider.PhoneNumberService
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class MessageToBotHandlerTest {
    @MockK
    lateinit var sendSmsCommandHelperMock: SendSmsCommandHelper

    @MockK
    lateinit var phoneNumberServiceMock: PhoneNumberService

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @InjectMockKs
    lateinit var cut: MessageToBotHandler

    @MockK
    lateinit var roomMock: AppserviceRoom

    @MockK
    lateinit var contextMock: MessageContext

    @BeforeEach
    fun beforeEach() {
        every { smsBridgePropertiesMock.templates.botTooManyMembers }.returns("tooMany")
        every { smsBridgePropertiesMock.templates.botHelp }.returns("help")
        every { smsBridgePropertiesMock.templates.botSmsSendError }.returns("error {error} {receiverNumbers}")
        coEvery { contextMock.answer(any(), any()) }.returns("someMessageId")
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
        coEvery { sendSmsCommandHelperMock.createRoomAndSendMessage(any(), any(), any(), any(), any(), any()) }
                .returns("message send")
        every { smsBridgePropertiesMock.defaultRegion }.returns("DE")
        every { phoneNumberServiceMock.parseToInternationalNumber(any()) }.returns("+4917392837462")

        val result = runBlocking {
            cut.handleMessage(
                    roomMock,
                    "sms send -t 017392837462 'some Text'",
                    "someUserId2",
                    contextMock
            )
        }
        assertThat(result).isTrue()
        coVerify(exactly = 1) {
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
        val result = runBlocking {
            cut.handleMessage(roomMock, "sms bla", "someUserId2", contextMock)
        }
        assertThat(result).isTrue()
        coVerify { contextMock.answer(match<NoticeMessageEventContent> { it.body == "tooMany" }) }
    }

    @Test
    fun `should answer with help when not sms command`() {
        val result = runBlocking {
            cut.handleMessage(roomMock, "bla", "someUserId2", contextMock)
        }
        assertThat(result).isTrue()
        coVerify { contextMock.answer(match<NoticeMessageEventContent> { it.body == "help" }) }
    }

    @Test
    fun `should not allow managed user to run command`() {
        val result1 = runBlocking {
            cut.handleMessage(roomMock, "sms send", "someUserId1", contextMock)
        }
        assertThat(result1).isFalse()

        val result2 = runBlocking {
            cut.handleMessage(roomMock, "sms send", "notKnownUserId", contextMock)
        }
        assertThat(result2).isFalse()

        coVerify { contextMock wasNot Called }
    }

    @Test
    fun `should do nothing when all members are managed`() {
        every { roomMock.members.keys }.returns(
                mutableSetOf(
                        AppserviceUser("someUserId1", true),
                        AppserviceUser("someUserId2", true)
                )
        )
        val result = runBlocking {
            cut.handleMessage(roomMock, "bla", "someUserId1", contextMock)
        }
        assertThat(result).isFalse()
        coVerify { contextMock wasNot Called }
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
        val result = runBlocking {
            cut.handleMessage(roomMock, "bla", "someUserId2", contextMock)
        }
        assertThat(result).isFalse()
        coVerify { contextMock wasNot Called }
    }

    @Test
    fun `should catch errors from SMSCommand`() {
        val result = runBlocking {
            cut.handleMessage(roomMock, "sms send bla", "someUserId2", contextMock)
        }
        assertThat(result).isTrue()
        coVerify {
            contextMock.answer(match<NoticeMessageEventContent> {
                it.body.contains(
                        "Error"
                )
            })
        }
    }

    @Test
    fun `should catch errors from unparsable command`() {
        val result = runBlocking {
            cut.handleMessage(roomMock, "sms send \" bla", "someUserId2", contextMock)
        }
        assertThat(result).isTrue()
        coVerify {
            contextMock.answer(match<NoticeMessageEventContent> {
                it.body == "error unbalanced quotes in  send \" bla unknown"
            })
        }
    }
}