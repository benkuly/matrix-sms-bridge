package net.folivo.matrix.bridge.sms.handler

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.ALWAYS
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.NO
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import net.folivo.matrix.restclient.api.rooms.Preset.TRUSTED_PRIVATE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class SendSmsCommandHelperTest {
    @MockK
    lateinit var roomServiceMock: SmsMatrixAppserviceRoomService

    @MockK
    lateinit var matrixClientMock: MatrixClient

    @MockK
    lateinit var botPropertiesMock: MatrixBotProperties

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @InjectMockKs
    lateinit var cut: SendSmsCommandHelper

    @BeforeEach
    fun beforeEach() {
        every { botPropertiesMock.username }.returns("bot")
        every { botPropertiesMock.serverName }.returns("someServer")
        coEvery { matrixClientMock.roomsApi.createRoom(allAny()) }.returns("someRoomId")
        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }.returns("someId")
        coEvery { roomServiceMock.sendMessageLater(any()) } just Runs
        every { smsBridgePropertiesMock.templates.botSmsSendNewRoomMessage }.returns("newRoomMessage {sender} {body}")
        every { smsBridgePropertiesMock.templates.botSmsSendCreatedRoomAndSendMessage }.returns("create room and send message {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendSendMessage }.returns("send message {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendTooManyRooms }.returns("too many rooms {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendDisabledRoomCreation }.returns("disabled room creation {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendError }.returns("error {error} {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendNoMessage }.returns("no message {receiverNumbers}")
    }

    @Test
    fun `should create room, and send message later when room creation mode is AUTO and no room found`() {
        coEvery { roomServiceMock.getRoomsWithUsers(allAny()) }.returns(flowOf())

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    roomCreationMode = ALWAYS,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111")
            )
        }
        assertThat(result).isEqualTo("create room and send message +1111111111")

        coVerifyAll {
            roomServiceMock.getRoomsWithUsers(match {
                it.containsAll(listOf("someSender", "@sms_1111111111:someServer"))
            })
            matrixClientMock.roomsApi.createRoom(
                    name = "room name",
                    invite = setOf(
                            "someSender",
                            "@sms_1111111111:someServer"
                    ),
                    preset = TRUSTED_PRIVATE
            )
            roomServiceMock.sendMessageLater(
                    match {
                        it.roomId == "someRoomId"
                        && it.body == "newRoomMessage someSender some text"
                        && it.requiredReceiverIds == setOf("@sms_1111111111:someServer")
                    }
            )
        }
    }

    @Test
    fun `should create room, and send message later when room creation mode is ALWAYS`() {
        coEvery { roomServiceMock.getRoomsWithUsers(allAny()) }
                .returns(flowOf(mockk(), mockk()))

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    roomCreationMode = ALWAYS,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111", "+22222222")
            )
        }
        assertThat(result).isEqualTo("create room and send message +1111111111, +22222222")

        coVerifyAll {
            roomServiceMock.getRoomsWithUsers(match {
                it.containsAll(setOf("someSender", "@sms_1111111111:someServer", "@sms_22222222:someServer"))
            })
            matrixClientMock.roomsApi.createRoom(
                    name = "room name",
                    invite = setOf(
                            "someSender",
                            "@sms_1111111111:someServer",
                            "@sms_22222222:someServer"
                    ),
                    preset = TRUSTED_PRIVATE
            )
            roomServiceMock.sendMessageLater(
                    match {
                        it.roomId == "someRoomId"
                        && it.body == "newRoomMessage someSender some text"
                        && it.requiredReceiverIds == setOf("@sms_1111111111:someServer", "@sms_22222222:someServer")
                    }
            )
        }
    }

    @Test
    fun `should create room, and send no message when message is empty`() {
        coEvery { roomServiceMock.getRoomsWithUsers(allAny()) }
                .returns(flowOf(mockk(), mockk()))

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "  ",
                    sender = "someSender",
                    roomCreationMode = ALWAYS,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111", "+22222222")
            )
        }
        assertThat(result).isEqualTo("create room and send message +1111111111, +22222222")

        coVerifyAll {
            roomServiceMock.getRoomsWithUsers(match {
                it.containsAll(setOf("someSender", "@sms_1111111111:someServer", "@sms_22222222:someServer"))
            })
            matrixClientMock.roomsApi.createRoom(
                    name = "room name",
                    invite = setOf(
                            "someSender",
                            "@sms_1111111111:someServer",
                            "@sms_22222222:someServer"
                    ),
                    preset = TRUSTED_PRIVATE
            )
        }
        coVerify(exactly = 0) { roomServiceMock.sendMessageLater(any()) }
    }

    @Test
    fun `should send message immediately when one room exists`() {
        coEvery { roomServiceMock.getRoomsWithUsers(allAny()) }.returns(flowOf(mockk {
            every { roomId }.returns("someRoomId")
        }))
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }.returns(mockk {
            every { roomId }.returns("someRoomId")
            every { members }.returns(
                    mutableMapOf(
                            AppserviceUser(
                                    "smsUser",
                                    true
                            ) to MemberOfProperties(2),
                            AppserviceUser(
                                    "@bot:someServer",
                                    true
                            ) to MemberOfProperties(2)
                    )
            )
        })

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111")
            )
        }
        assertThat(result).isEqualTo("send message +1111111111")

        coVerify {
            matrixClientMock.roomsApi.sendRoomEvent(
                    roomId = "someRoomId",
                    eventContent = match<TextMessageEventContent> { it.body == "newRoomMessage someSender some text" },
                    txnId = any()
            )
        }
    }

    @Test
    fun `should not send message later when one room exists, but managed members does not match size`() {
        coEvery { roomServiceMock.getRoomsWithUsers(allAny()) }.returns(flowOf(mockk {
            every { roomId }.returns("someRoomId")
        }))
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }
                .returns(mockk {
                    every { roomId }.returns("someRoomId")
                    every { members }.returns(
                            mutableMapOf(
                                    AppserviceUser("someUser", true) to MemberOfProperties(1),
                                    AppserviceUser("someUserTooMany", true) to MemberOfProperties(1),
                                    AppserviceUser("@bot:someServer", true) to MemberOfProperties(1)
                            )
                    )
                })

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111")
            )
        }
        assertThat(result).isEqualTo("disabled room creation +1111111111")

        coVerify { matrixClientMock wasNot Called }
        coVerify(exactly = 0) { roomServiceMock.sendMessageLater(any()) }
    }

    @Test
    fun `should invite bot and send message when one room exists, but bot is not member`() {
        coEvery { matrixClientMock.roomsApi.inviteUser(any(), any(), any()) } just Runs
        coEvery { roomServiceMock.getRoomsWithUsers(allAny()) }.returns(flowOf(mockk {
            every { roomId }.returns("someRoomId")
        }))
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }
                .returns(mockk {
                    every { roomId }.returns("someRoomId")
                    every { members }.returns(
                            mutableMapOf(
                                    AppserviceUser("someOtherUser", false) to MemberOfProperties(1),
                                    AppserviceUser("@sms_1111111111:someServer", true) to MemberOfProperties(1)
                            )
                    )
                })

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111")
            )
        }
        assertThat(result).isEqualTo("send message +1111111111")

        coVerify {
            matrixClientMock.roomsApi.inviteUser(
                    roomId = "someRoomId",
                    userId = "@bot:someServer",
                    asUserId = "@sms_1111111111:someServer"
            )
            roomServiceMock.sendMessageLater(
                    match {
                        it.roomId == "someRoomId"
                        && it.body == "newRoomMessage someSender some text"
                        && it.requiredReceiverIds == setOf("@sms_1111111111:someServer")
                    }
            )
        }
    }

    @Test
    fun `should not send message when empty message content`() {
        coEvery { roomServiceMock.getRoomsWithUsers(allAny()) }.returns(flowOf(mockk {
            every { roomId }.returns("someRoomId")
        }))
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }
                .returns(mockk {
                    every { roomId }.returns("someRoomId")
                    every { members }.returns(
                            mutableMapOf(
                                    AppserviceUser("someUser", true) to MemberOfProperties(1),
                                    AppserviceUser("botUser", true) to MemberOfProperties(1)
                            )
                    )
                })

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = null,
                    sender = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111")
            )
        }
        assertThat(result).isEqualTo("no message +1111111111")

        verify { matrixClientMock wasNot Called }
        coVerify(exactly = 0) { roomServiceMock.sendMessageLater(any()) }
    }

    @Test
    fun `should not send message when to many rooms exists and room creation disabled`() {
        coEvery { roomServiceMock.getRoomsWithUsers(allAny()) }
                .returns(flowOf(mockk(), mockk()))

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111")
            )
        }
        assertThat(result).isEqualTo("too many rooms +1111111111")

        verify { matrixClientMock wasNot Called }
        coVerify(exactly = 0) { roomServiceMock.sendMessageLater(any()) }
    }

    @Test
    fun `should not send message when room creation disabled`() {
        coEvery { roomServiceMock.getRoomsWithUsers(allAny()) }
                .returns(flowOf())

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111")
            )
        }
        assertThat(result).isEqualTo("disabled room creation +1111111111")

        verify { matrixClientMock wasNot Called }
        coVerify(exactly = 0) { roomServiceMock.sendMessageLater(any()) }
    }

    @Test
    fun `should not send message but catch errors`() {
        coEvery { roomServiceMock.getRoomsWithUsers(allAny()) }
                .returns(flowOf())
        coEvery { matrixClientMock.roomsApi.createRoom(allAny()) }.throws(RuntimeException("unicorn"))

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    sender = "someSender",
                    roomCreationMode = ALWAYS,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111")
            )
        }
        assertThat(result).isEqualTo("error unicorn +1111111111")

        val roomsApi = matrixClientMock.roomsApi
        coVerify(exactly = 0) {
            roomServiceMock.sendMessageLater(any())
            roomsApi.sendRoomEvent(any(), any(), any(), any(), any())
        }
    }
}