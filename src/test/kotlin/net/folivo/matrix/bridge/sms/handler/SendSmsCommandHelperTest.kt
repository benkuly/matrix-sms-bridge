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
import net.folivo.matrix.bridge.sms.membership.Membership
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.restclient.MatrixClient
import net.folivo.matrix.restclient.api.rooms.Preset.TRUSTED_PRIVATE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

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

    lateinit var room: AppserviceRoom

    @BeforeEach
    fun beforeEach() {
        room = mockk {
            every { roomId } returns "someRoomId"
        }
        every { botPropertiesMock.username }.returns("bot")
        every { botPropertiesMock.serverName }.returns("someServer")
        coEvery { matrixClientMock.roomsApi.createRoom(allAny()) }.returns("someRoomId")
        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }.returns("someId")
        coEvery { roomServiceMock.sendRoomMessage(any()) } just Runs
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }.returns(room)
        every { smsBridgePropertiesMock.defaultTimeZone }.returns("Europe/Berlin")
        every { smsBridgePropertiesMock.templates.botSmsSendNewRoomMessage }.returns("newRoomMessage {sender} {body}")
        every { smsBridgePropertiesMock.templates.botSmsSendCreatedRoomAndSendMessage }.returns("create room and send message {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendSendMessage }.returns("send message {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendTooManyRooms }.returns("too many rooms {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendDisabledRoomCreation }.returns("disabled room creation {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendError }.returns("error {error} {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendNoMessage }.returns("no message {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendNoticeDelayedMessage }.returns("notice at {sendAfter}")
    }

    @Test
    fun `should create room, and send message later when room creation mode is AUTO and no room found`() {
        coEvery { roomServiceMock.getRoomsWithMembers(allAny()) }.returns(flowOf())

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    senderId = "someSender",
                    roomCreationMode = ALWAYS,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111"),
                    sendAfterLocal = null
            )
        }
        assertThat(result).isEqualTo("create room and send message +1111111111")

        coVerify {
            roomServiceMock.getRoomsWithMembers(match {
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
            roomServiceMock.sendRoomMessage(
                    match {
                        it.room == room
                        && it.body == "newRoomMessage someSender some text"
                        && it.requiredReceiverIds == setOf("@sms_1111111111:someServer")
                    }
            )
        }
    }

    @Test
    fun `should create room, and send message later when room creation mode is ALWAYS`() {
        coEvery { roomServiceMock.getRoomsWithMembers(allAny()) }
                .returns(flowOf(mockk(), mockk()))

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    senderId = "someSender",
                    roomCreationMode = ALWAYS,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111", "+22222222"),
                    sendAfterLocal = null
            )
        }
        assertThat(result).isEqualTo("create room and send message +1111111111, +22222222")

        coVerify {
            roomServiceMock.getRoomsWithMembers(match {
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
            roomServiceMock.sendRoomMessage(
                    match {
                        it.room == room
                        && it.body == "newRoomMessage someSender some text"
                        && it.requiredReceiverIds == setOf("@sms_1111111111:someServer", "@sms_22222222:someServer")
                    }
            )
        }
    }

    @Test
    fun `should create room, and send no message when message is empty`() {
        coEvery { roomServiceMock.getRoomsWithMembers(allAny()) }
                .returns(flowOf(mockk(), mockk()))

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "  ",
                    senderId = "someSender",
                    roomCreationMode = ALWAYS,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111", "+22222222"),
                    sendAfterLocal = null
            )
        }
        assertThat(result).isEqualTo("create room and send message +1111111111, +22222222")

        coVerifyAll {
            roomServiceMock.getRoomsWithMembers(match {
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
        coVerify(exactly = 0) { roomServiceMock.sendRoomMessage(any()) }
    }

    @Test
    fun `should send message immediately when one room exists`() {
        coEvery { roomServiceMock.getRoomsWithMembers(allAny()) }.returns(flowOf(mockk {
            every { roomId }.returns("someRoomId")
        }))
        val roomMock = mockk<AppserviceRoom> {
            every { roomId }.returns("someRoomId")
            every { memberships }.returns(
                    listOf(
                            Membership(AppserviceUser("smsUser", true), 2),
                            Membership(AppserviceUser("@bot:someServer", true), 2),
                    )
            )
        }
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }.returns(roomMock)

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    senderId = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111"),
                    sendAfterLocal = null
            )
        }
        assertThat(result).isEqualTo("send message +1111111111")

        coVerify {
            roomServiceMock.sendRoomMessage(
                    match {
                        it.room == roomMock
                        && it.body == "newRoomMessage someSender some text"
                        && Instant.now().until(it.sendAfter, ChronoUnit.MINUTES) < 1
                        && it.requiredReceiverIds.containsAll(setOf("@sms_1111111111:someServer"))
                        && !it.isNotice
                    }
            )
        }
    }

    @Test
    fun `should not send message later when one room exists, but managed members does not match size`() {
        coEvery { roomServiceMock.getRoomsWithMembers(allAny()) }.returns(flowOf(mockk {
            every { roomId }.returns("someRoomId")
        }))
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }
                .returns(mockk {
                    every { roomId }.returns("someRoomId")
                    every { memberships }.returns(
                            listOf(
                                    Membership(AppserviceUser("someUser", true), 1),
                                    Membership(AppserviceUser("someUserTooMany", true), 1),
                                    Membership(AppserviceUser("@bot:someServer", true), 1)
                            )
                    )
                })

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    senderId = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111"),
                    sendAfterLocal = null
            )
        }
        assertThat(result).isEqualTo("disabled room creation +1111111111")

        coVerify { matrixClientMock wasNot Called }
        coVerify(exactly = 0) { roomServiceMock.sendRoomMessage(any()) }
    }

    @Test
    fun `should invite bot and send message when one room exists, but bot is not member`() {
        coEvery { matrixClientMock.roomsApi.inviteUser(any(), any(), any()) } just Runs
        coEvery { roomServiceMock.getRoomsWithMembers(allAny()) }.returns(flowOf(mockk {
            every { roomId }.returns("someRoomId")
        }))
        every { room.memberships }.returns(
                listOf(
                        Membership(AppserviceUser("someOtherUser", false), 1),
                        Membership(AppserviceUser("@sms_1111111111:someServer", true), 1)
                )
        )
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }
                .returns(room)

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    senderId = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111"),
                    sendAfterLocal = null
            )
        }
        assertThat(result).isEqualTo("send message +1111111111")

        coVerify {
            matrixClientMock.roomsApi.inviteUser(
                    roomId = "someRoomId",
                    userId = "@bot:someServer",
                    asUserId = "@sms_1111111111:someServer"
            )
            roomServiceMock.sendRoomMessage(
                    match {
                        it.room == room
                        && it.body == "newRoomMessage someSender some text"
                        && it.requiredReceiverIds == setOf("@sms_1111111111:someServer")
                    }
            )
        }
    }

    @Test
    fun `should not send message when empty message content`() {
        coEvery { roomServiceMock.getRoomsWithMembers(allAny()) }.returns(flowOf(mockk {
            every { roomId }.returns("someRoomId")
        }))
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }
                .returns(mockk {
                    every { roomId }.returns("someRoomId")
                    every { memberships }.returns(
                            listOf(
                                    Membership(AppserviceUser("someUser", true), 1),
                                    Membership(AppserviceUser("botUser", true), 1)
                            )
                    )
                })

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = null,
                    senderId = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111"),
                    sendAfterLocal = null
            )
        }
        assertThat(result).isEqualTo("no message +1111111111")

        verify { matrixClientMock wasNot Called }
        coVerify(exactly = 0) { roomServiceMock.sendRoomMessage(any()) }
    }

    @Test
    fun `should not send message when to many rooms exists and room creation disabled`() {
        coEvery { roomServiceMock.getRoomsWithMembers(allAny()) }
                .returns(flowOf(mockk(), mockk()))

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    senderId = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111"),
                    sendAfterLocal = null
            )
        }
        assertThat(result).isEqualTo("too many rooms +1111111111")

        verify { matrixClientMock wasNot Called }
        coVerify(exactly = 0) { roomServiceMock.sendRoomMessage(any()) }
    }

    @Test
    fun `should not send message when room creation disabled`() {
        coEvery { roomServiceMock.getRoomsWithMembers(allAny()) }
                .returns(flowOf())

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    senderId = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111"),
                    sendAfterLocal = null
            )
        }
        assertThat(result).isEqualTo("disabled room creation +1111111111")

        verify { matrixClientMock wasNot Called }
        coVerify(exactly = 0) { roomServiceMock.sendRoomMessage(any()) }
    }

    @Test
    fun `should not send message but catch errors`() {
        coEvery { roomServiceMock.getRoomsWithMembers(allAny()) }
                .returns(flowOf())
        coEvery { matrixClientMock.roomsApi.createRoom(allAny()) }.throws(RuntimeException("unicorn"))

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    senderId = "someSender",
                    roomCreationMode = ALWAYS,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111"),
                    sendAfterLocal = null
            )
        }
        assertThat(result).isEqualTo("error unicorn +1111111111")

        val roomsApi = matrixClientMock.roomsApi
        coVerify(exactly = 0) {
            roomServiceMock.sendRoomMessage(any())
            roomsApi.sendRoomEvent(any(), any(), any(), any(), any())
        }
    }


    @Test
    fun `should send message in future and notify user about that when bot is member`() {
        coEvery { roomServiceMock.getRoomsWithMembers(allAny()) }.returns(flowOf(mockk {
            every { roomId }.returns("someRoomId")
        }))
        every { room.memberships }.returns(
                listOf(
                        Membership(AppserviceUser("@bot:someServer", true), 1),
                        Membership(AppserviceUser("@sms_1111111111:someServer", true), 1)
                )
        )
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }
                .returns(room)

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    senderId = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111"),
                    sendAfterLocal = LocalDateTime.of(2055, 11, 9, 12, 0)
            )
        }
        assertThat(result).isEqualTo("send message +1111111111")

        coVerify {
            roomServiceMock.sendRoomMessage(
                    match {
                        it.room == room
                        && it.body == "newRoomMessage someSender some text"
                        && it.requiredReceiverIds == setOf("@sms_1111111111:someServer")
                        && !it.isNotice
                    }
            )
            roomServiceMock.sendRoomMessage(
                    match {
                        it.room == room
                        && it.body == "notice at 2055-11-09T12:00"
                        && it.requiredReceiverIds == setOf("@sms_1111111111:someServer")
                        && it.isNotice
                    }
            )
        }
    }

    @Test
    fun `should send message in future and notify user about that when bot is not member`() {
        coEvery { matrixClientMock.roomsApi.inviteUser(any(), any(), any()) } just Runs
        coEvery { roomServiceMock.getRoomsWithMembers(allAny()) }.returns(flowOf(mockk {
            every { roomId }.returns("someRoomId")
        }))
        every { room.memberships }.returns(
                listOf(
                        Membership(AppserviceUser("someOtherUser", false), 1),
                        Membership(AppserviceUser("@sms_1111111111:someServer", true), 1)
                )
        )
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }
                .returns(room)

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    senderId = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111"),
                    sendAfterLocal = LocalDateTime.of(2055, 11, 9, 12, 0)
            )
        }
        assertThat(result).isEqualTo("send message +1111111111")

        coVerify {
            matrixClientMock.roomsApi.inviteUser(
                    roomId = "someRoomId",
                    userId = "@bot:someServer",
                    asUserId = "@sms_1111111111:someServer"
            )
            roomServiceMock.sendRoomMessage(
                    match {
                        it.room == room
                        && it.body == "newRoomMessage someSender some text"
                        && it.requiredReceiverIds == setOf("@sms_1111111111:someServer")
                    }
            )
            roomServiceMock.sendRoomMessage(
                    match {
                        it.room == room
                        && it.body == "notice at 2055-11-09T12:00"
                        && it.requiredReceiverIds == setOf("@sms_1111111111:someServer")
                    }
            )
        }
    }

    @Test
    fun `should send message but not notify user about that, when in sendAfter is in past and bot is not member`() {
        coEvery { matrixClientMock.roomsApi.inviteUser(any(), any(), any()) } just Runs
        coEvery { roomServiceMock.getRoomsWithMembers(allAny()) }.returns(flowOf(mockk {
            every { roomId }.returns("someRoomId")
        }))
        every { room.memberships }.returns(
                listOf(
                        Membership(AppserviceUser("someOtherUser", false), 1),
                        Membership(AppserviceUser("@sms_1111111111:someServer", true), 1)
                )
        )
        coEvery { roomServiceMock.getOrCreateRoom("someRoomId") }
                .returns(room)

        val result = runBlocking {
            cut.createRoomAndSendMessage(
                    body = "some text",
                    senderId = "someSender",
                    roomCreationMode = NO,
                    roomName = "room name",
                    receiverNumbers = listOf("+1111111111"),
                    sendAfterLocal = LocalDateTime.of(1955, 11, 9, 12, 0)
            )
        }
        assertThat(result).isEqualTo("send message +1111111111")

        coVerify {
            matrixClientMock.roomsApi.inviteUser(
                    roomId = "someRoomId",
                    userId = "@bot:someServer",
                    asUserId = "@sms_1111111111:someServer"
            )
            roomServiceMock.sendRoomMessage(
                    match {
                        it.room == room
                        && it.body == "newRoomMessage someSender some text"
                        && it.requiredReceiverIds == setOf("@sms_1111111111:someServer")
                    }
            )
        }
        coVerify(exactly = 0) {
            roomServiceMock.sendRoomMessage(
                    match {
                        it.room == room
                        && it.body.startsWith("notice")
                        && it.requiredReceiverIds == setOf("@sms_1111111111:someServer")
                    }
            )
        }
    }
}