package net.folivo.matrix.bridge.sms.handler

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.appservice.api.AppserviceHandlerHelper
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.ALWAYS
import net.folivo.matrix.bridge.sms.handler.SendSmsCommandHelper.RoomCreationMode.NO
import net.folivo.matrix.bridge.sms.room.AppserviceRoomRepository
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import net.folivo.matrix.restclient.api.rooms.Preset.TRUSTED_PRIVATE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus.FORBIDDEN
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class SendSmsCommandHelperTest {
    @MockK
    lateinit var roomRepositoryMock: AppserviceRoomRepository

    @MockK
    lateinit var helperMock: AppserviceHandlerHelper

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
        coEvery { matrixClientMock.roomsApi.joinRoom(allAny()) }.returns("someRoomId")
        coEvery { matrixClientMock.roomsApi.sendRoomEvent(allAny(), any()) }.returns("someEventId")
        coEvery { helperMock.registerAndSaveUser(any()) } just Runs
        every { smsBridgePropertiesMock.templates.botSmsSendNewRoomMessage }.returns("newRoomMessage {sender} {body}")
        every { smsBridgePropertiesMock.templates.botSmsSendCreatedRoomAndSendMessage }.returns("create room and send message {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendSendMessage }.returns("send message {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendTooManyRooms }.returns("too many rooms {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendDisabledRoomCreation }.returns("disabled room creation {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendError }.returns("error {error} {receiverNumbers}")
        every { smsBridgePropertiesMock.templates.botSmsSendNoMessage }.returns("no message {receiverNumbers}")

    }

    @Test
    fun `should create room, join users and send message when room creation mode is AUTO and no room found`() {
        every { roomRepositoryMock.findByMembersUserIdContaining(allAny()) }
                .returns(Flux.empty())

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
            matrixClientMock.roomsApi.createRoom(
                    name = "room name",
                    invite = setOf(
                            "someSender",
                            "@sms_1111111111:someServer"
                    ),
                    preset = TRUSTED_PRIVATE
            )
            matrixClientMock.roomsApi.joinRoom("someRoomId", asUserId = "@sms_1111111111:someServer")
            matrixClientMock.roomsApi.sendRoomEvent(
                    roomId = "someRoomId",
                    eventContent = match<TextMessageEventContent> { it.body == "newRoomMessage someSender some text" },
                    txnId = any()
            )
            roomRepositoryMock.findByMembersUserIdContaining(match {
                it.containsAll(listOf("someSender", "@sms_1111111111:someServer"))
            })
        }
    }

    @Test
    fun `should create room, join users and send message when room creation mode is ALWAYS`() {
        every { roomRepositoryMock.findByMembersUserIdContaining(allAny()) }
                .returns(Flux.just(mockk(), mockk()))

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
            matrixClientMock.roomsApi.createRoom(
                    name = "room name",
                    invite = setOf(
                            "someSender",
                            "@sms_1111111111:someServer",
                            "@sms_22222222:someServer"
                    ),
                    preset = TRUSTED_PRIVATE
            )
            matrixClientMock.roomsApi.joinRoom("someRoomId", asUserId = "@sms_1111111111:someServer")
            matrixClientMock.roomsApi.joinRoom("someRoomId", asUserId = "@sms_22222222:someServer")
            matrixClientMock.roomsApi.sendRoomEvent(
                    roomId = "someRoomId",
                    eventContent = match<TextMessageEventContent> { it.body == "newRoomMessage someSender some text" },
                    txnId = any()
            )
            roomRepositoryMock.findByMembersUserIdContaining(match {
                it.containsAll(setOf("someSender", "@sms_1111111111:someServer", "@sms_22222222:someServer"))
            })
        }
    }

    @Test
    fun `should create room, join users and send no message when message is empty`() {
        every { roomRepositoryMock.findByMembersUserIdContaining(allAny()) }
                .returns(Flux.just(mockk(), mockk()))

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

        val roomsApi = matrixClientMock.roomsApi
        coVerifyAll {
            roomsApi.createRoom(
                    name = "room name",
                    invite = setOf(
                            "someSender",
                            "@sms_1111111111:someServer",
                            "@sms_22222222:someServer"
                    ),
                    preset = TRUSTED_PRIVATE
            )
            roomsApi.joinRoom("someRoomId", asUserId = "@sms_1111111111:someServer")
            roomsApi.joinRoom("someRoomId", asUserId = "@sms_22222222:someServer")
            roomRepositoryMock.findByMembersUserIdContaining(match {
                it.containsAll(setOf("someSender", "@sms_1111111111:someServer", "@sms_22222222:someServer"))
            })
        }
        coVerify(exactly = 0) {
            roomsApi.sendRoomEvent(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `should create user and join when user does not exists`() {
        every { roomRepositoryMock.findByMembersUserIdContaining(allAny()) }
                .returns(Flux.just(mockk(), mockk()))
        coEvery { matrixClientMock.roomsApi.joinRoom(allAny()) }
                .throws(
                        MatrixServerException(
                                FORBIDDEN,
                                ErrorResponse("FORBIDDEN", "user does not exists")
                        )
                ).coAndThen { "someRoomId" }

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
            matrixClientMock.roomsApi.createRoom(
                    name = "room name",
                    invite = setOf(
                            "someSender",
                            "@sms_1111111111:someServer"
                    ),
                    preset = TRUSTED_PRIVATE
            )
            helperMock.registerAndSaveUser("@sms_1111111111:someServer")
            matrixClientMock.roomsApi.joinRoom("someRoomId", asUserId = "@sms_1111111111:someServer")
            matrixClientMock.roomsApi.joinRoom("someRoomId", asUserId = "@sms_1111111111:someServer")
            matrixClientMock.roomsApi.sendRoomEvent(
                    roomId = "someRoomId",
                    eventContent = match<TextMessageEventContent> { it.body == "newRoomMessage someSender some text" },
                    txnId = any()
            )
            roomRepositoryMock.findByMembersUserIdContaining(match {
                it.containsAll(setOf("someSender", "@sms_1111111111:someServer"))
            })
        }
    }

    @Test
    fun `should send message when one room exists`() {
        every { roomRepositoryMock.findByMembersUserIdContaining(allAny()) }.returns(Flux.just(mockk {
            every { roomId }.returns("someRoomId")
        }))
        every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.just(mockk {
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
        }))

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
    fun `should not send message when one room exists, but managed members does not match size`() {
        every { roomRepositoryMock.findByMembersUserIdContaining(allAny()) }.returns(Flux.just(mockk {
            every { roomId }.returns("someRoomId")
        }))
        every { roomRepositoryMock.findById("someRoomId") }
                .returns(Mono.just(mockk {
                    every { roomId }.returns("someRoomId")
                    every { members }.returns(
                            mutableMapOf(
                                    AppserviceUser("someUser", true) to MemberOfProperties(1),
                                    AppserviceUser("someUserTooMany", true) to MemberOfProperties(1),
                                    AppserviceUser("@bot:someServer", true) to MemberOfProperties(1)
                            )
                    )
                }))

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
    }

    @Test
    fun `should invite bot and send message when one room exists, but bot is not member`() {
        coEvery { matrixClientMock.roomsApi.inviteUser(any(), any(), any()) } just Runs
        every { roomRepositoryMock.findByMembersUserIdContaining(allAny()) }.returns(Flux.just(mockk {
            every { roomId }.returns("someRoomId")
        }))
        every { roomRepositoryMock.findById("someRoomId") }
                .returns(Mono.just(mockk {
                    every { roomId }.returns("someRoomId")
                    every { members }.returns(
                            mutableMapOf(
                                    AppserviceUser("someOtherUser", false) to MemberOfProperties(1),
                                    AppserviceUser("@sms_1111111111:someServer", true) to MemberOfProperties(1)
                            )
                    )
                }))

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
            matrixClientMock.roomsApi.sendRoomEvent(
                    roomId = "someRoomId",
                    eventContent = match<TextMessageEventContent> { it.body == "newRoomMessage someSender some text" },
                    txnId = any()
            )
        }
    }

    @Test
    fun `should not send message when empty message content`() {
        every { roomRepositoryMock.findByMembersUserIdContaining(allAny()) }.returns(Flux.just(mockk {
            every { roomId }.returns("someRoomId")
        }))
        every { roomRepositoryMock.findById("someRoomId") }
                .returns(Mono.just(mockk {
                    every { roomId }.returns("someRoomId")
                    every { members }.returns(
                            mutableMapOf(
                                    AppserviceUser("someUser", true) to MemberOfProperties(1),
                                    AppserviceUser("botUser", true) to MemberOfProperties(1)
                            )
                    )
                }))

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
    }

    @Test
    fun `should not send message when to many rooms exists and room creation disabled`() {
        every { roomRepositoryMock.findByMembersUserIdContaining(allAny()) }
                .returns(Flux.just(mockk(), mockk()))

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
    }

    @Test
    fun `should not send message when room creation disabled`() {
        every { roomRepositoryMock.findByMembersUserIdContaining(allAny()) }
                .returns(Flux.empty())

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
    }

    @Test
    fun `should not send message but catch errors`() {
        every { roomRepositoryMock.findByMembersUserIdContaining(allAny()) }
                .returns(Flux.empty())
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

        val roomsApiMock = matrixClientMock.roomsApi
        coVerify(exactly = 0) {
            roomsApiMock.sendRoomEvent(allAny(), any())
            roomsApiMock.joinRoom(allAny())
        }
    }
}