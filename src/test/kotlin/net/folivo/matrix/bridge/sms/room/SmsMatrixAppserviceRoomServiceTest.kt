package net.folivo.matrix.bridge.sms.room

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.bridge.sms.user.SmsMatrixAppserviceUserService
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import net.folivo.matrix.restclient.api.rooms.GetJoinedMembersResponse
import net.folivo.matrix.restclient.api.rooms.GetJoinedMembersResponse.RoomMember
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus.FORBIDDEN
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

@ExtendWith(MockKExtension::class)
class SmsMatrixAppserviceRoomServiceTest {

    @MockK
    lateinit var roomRepositoryMock: AppserviceRoomRepository

    @MockK
    lateinit var userServiceMock: SmsMatrixAppserviceUserService

    @MockK
    lateinit var messageRepositoryMock: RoomMessageRepository

    @MockK
    lateinit var botPropertiesMock: MatrixBotProperties

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @MockK
    lateinit var matrixClientMock: MatrixClient

    lateinit var cut: SmsMatrixAppserviceRoomService

    @BeforeEach
    fun beforeEach() {
        every { botPropertiesMock.username }.returns("bot")
        every { botPropertiesMock.serverName }.returns("someServer")
        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)
        every { messageRepositoryMock.delete(any()) }.returns(Mono.empty())


        cut = spyk(
                SmsMatrixAppserviceRoomService(
                        roomRepositoryMock,
                        messageRepositoryMock,
                        userServiceMock,
                        matrixClientMock,
                        botPropertiesMock,
                        smsBridgePropertiesMock
                )
        )
    }

    @Test
    fun `roomExistingState should always be DOES_NOT_EXIST`() {
        val result = runBlocking {
            cut.roomExistingState("someRoomAlias")
        }
        assertThat(result).isEqualTo(DOES_NOT_EXISTS)
    }

    @Test
    fun `should not save room in database`() {
        runBlocking {
            cut.saveRoom("someRoomAlias", "someRoomId")
        }
        verify { roomRepositoryMock wasNot Called }
    }

    @Test
    fun `should save user join to room in database`() {
        val room = AppserviceRoom("someRoomId", mutableMapOf(mockk<AppserviceUser>() to mockk()))
        val user = AppserviceUser("someUserId", true)
        every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
        coEvery { userServiceMock.getUser("someUserId") }.returns(user)
        coEvery { userServiceMock.getLastMappingToken("someUserId") }.returns(23)
        every { roomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))

        runBlocking {
            cut.saveRoomJoin("someRoomId", "someUserId")
        }

        verify {
            roomRepositoryMock.save<AppserviceRoom>(match {
                it.members.contains(user)
                && it.members[user] == MemberOfProperties(24)
            })
        }
    }

    @Test
    fun `should save user join to room in database even if room does not exists yet`() {
        val user = AppserviceUser("someUserId", true)
        val existingUser = AppserviceUser("someExistingUserId", false)
        val roomWithoutMember = AppserviceRoom("someRoomId")
        val roomWithMember = AppserviceRoom(
                "someRoomId", mutableMapOf(
                existingUser to MemberOfProperties(1),
                user to MemberOfProperties(1)
        )
        )
        coEvery { userServiceMock.getUser("someExistingUserId") }.returns(existingUser)
        coEvery { userServiceMock.getUser("someUserId") }.returns(user)
        coEvery { userServiceMock.getLastMappingToken("someExistingUserId") }.returns(0)
        coEvery { userServiceMock.getLastMappingToken("someUserId") }.returns(0)
        every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.empty())
        every { roomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(roomWithoutMember))
        coEvery { matrixClientMock.roomsApi.getJoinedMembers(allAny()) }.returns(
                GetJoinedMembersResponse(
                        mapOf(
                                "someExistingUserId" to mockk(),
                                "someUserId" to mockk()
                        )
                )
        )

        runBlocking {
            cut.saveRoomJoin("someRoomId", "someUserId")
        }

        verify {
            roomRepositoryMock.save<AppserviceRoom>(roomWithMember)
        }
    }

    @Test
    fun `should save user room leave in database`() {
        val user1 = AppserviceUser("someUserId1", false)
        val user2 = AppserviceUser("someUserId2", false)
        val user3 = AppserviceUser("someUserId3", true)

        val room = AppserviceRoom(
                "someRoomId", mutableMapOf(
                user1 to MemberOfProperties(1),
                user2 to MemberOfProperties(1),
                user3 to MemberOfProperties(1)
        )
        )
        every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
        every { roomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))

        runBlocking {
            cut.saveRoomLeave("someRoomId", "someUserId1")
        }

        verify {
            roomRepositoryMock.save<AppserviceRoom>(match {
                it.members.keys.containsAll(listOf(user2, user3))
            })
        }
    }

    @Test
    fun `should save user room leave in database and leave room when all users are managed users`() {
        val user1 = AppserviceUser("someUserId1", false)
        val user2 = AppserviceUser("someUserId2", true)
        val user3 = AppserviceUser("@bot:someServer", true)

        val room = AppserviceRoom(
                "someRoomId", mutableMapOf(
                user1 to MemberOfProperties(1),
                user2 to MemberOfProperties(1),
                user3 to MemberOfProperties(1)
        )
        )
        every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
        coEvery { matrixClientMock.roomsApi.leaveRoom(allAny()) } just Runs
        every { roomRepositoryMock.delete(any()) }.returns(Mono.empty())

        runBlocking {
            cut.saveRoomLeave("someRoomId", "someUserId1")
        }

        verifyAll {
            roomRepositoryMock.findById("someRoomId")
            roomRepositoryMock.delete(match {
                it.members.keys.containsAll(listOf(user2, user3))
            })
        }
        coVerifyAll {
            matrixClientMock.roomsApi.leaveRoom("someRoomId", "someUserId2")
            matrixClientMock.roomsApi.leaveRoom("someRoomId", null)
        }
    }

    @Test
    fun `should get room without id`() {
        val room = AppserviceRoom("someRoomId2")
        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)
        coEvery { userServiceMock.getUser("someUserId") }
                .returns(
                        AppserviceUser(
                                "someUserId", true, mutableMapOf(
                                AppserviceRoom("someRoomId1") to MemberOfProperties(12),
                                room to MemberOfProperties(24)
                        )
                        )

                )
        val result = runBlocking { cut.getRoom("someUserId", 24) }
        assertThat(result).isEqualTo(room)
    }

    @Test
    fun `should get room without id when mapping token forced`() {
        val room = AppserviceRoom("someRoomId1")

        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)
        coEvery { userServiceMock.getUser("someUserId") }
                .returns(
                        AppserviceUser(
                                "someUserId", true, mutableMapOf(
                                room to MemberOfProperties(12)
                        )
                        )
                )
        val result = runBlocking { cut.getRoom("someUserId", 12) }
        assertThat(result).isEqualTo(room)

    }

    @Test
    fun `should get room without id when mapping token can be ignored`() {
        val room = AppserviceRoom("someRoomId1")
        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(true)
        coEvery { userServiceMock.getUser("someUserId") }
                .returns(
                        AppserviceUser(
                                "someUserId", true, mutableMapOf(
                                room to MemberOfProperties(12)
                        )
                        )
                )
        val result = runBlocking { cut.getRoom("someUserId", 24) }
        assertThat(result).isEqualTo(room)

    }

    @Test
    fun `should not get room without id`() {
        coEvery { userServiceMock.getUser("someUserId") }
                .returns(
                        AppserviceUser(
                                "someUserId", true, mutableMapOf(
                                AppserviceRoom("someRoomId1") to MemberOfProperties(12)
                        )
                        )
                )
        val result = runBlocking { cut.getRoom("someUserId", 24) }
        assertThat(result).isNull()
    }

    @Test
    fun `should get room`() {
        val room = AppserviceRoom("someRoomId", mutableMapOf(AppserviceUser("userId1", true) to MemberOfProperties(1)))
        every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
        val result = runBlocking { cut.getOrCreateRoom("someRoomId") }
        assertThat(result).isEqualTo(room)
    }

    @Test
    fun `should create room and fetch all members`() {
        val user1 = AppserviceUser("userId1", true)
        val user2 = AppserviceUser("userId2", false)
        val roomWithoutUsers = AppserviceRoom("someRoomId")
        val roomWithUsers = AppserviceRoom(
                "someRoomId",
                mutableMapOf(user1 to MemberOfProperties(1), user2 to MemberOfProperties(24))
        )

        every { roomRepositoryMock.save<AppserviceRoom>(any()) }.returnsMany(
                Mono.just(roomWithoutUsers),
                Mono.just(roomWithUsers)
        )
        every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.empty())
        coEvery { matrixClientMock.roomsApi.getJoinedMembers("someRoomId") }
                .returns(GetJoinedMembersResponse(mapOf("userId1" to RoomMember(), "userId2" to RoomMember())))
        coEvery { userServiceMock.getUser("userId1") }.returns(user1)
        coEvery { userServiceMock.getUser("userId2") }.returns(user2)
        coEvery { userServiceMock.getLastMappingToken("userId1") }.returns(0)
        coEvery { userServiceMock.getLastMappingToken("userId2") }.returns(23)

        val result = runBlocking { cut.getOrCreateRoom("someRoomId") }

        assertThat(result).isEqualTo(roomWithUsers)
        verify {
            roomRepositoryMock.save<AppserviceRoom>(roomWithUsers)
        }
    }

    @Test
    fun `should get rooms with users`() {
        val rooms = arrayOf(AppserviceRoom("roomId1"), AppserviceRoom("roomId2"))
        every { roomRepositoryMock.findByMembersUserIdContaining(any()) }
                .returns(Flux.just(*rooms))
        val result = runBlocking { cut.getRoomsWithUsers(setOf("userId1", "userId2")).toList() }
        assertThat(result).containsAll(rooms.asList())
    }

    @Test
    fun `should send message later`() {
        val message = RoomMessage(mockk(), "some body", Instant.ofEpochMilli(123))
        every { messageRepositoryMock.save<RoomMessage>(any()) }.returns(Mono.just(message))
        runBlocking { cut.sendRoomMessage(message) }
        verify { messageRepositoryMock.save<RoomMessage>(message) }
    }

    @Test
    fun `should send and delete messages`() {
        val room = AppserviceRoom(
                "someRoomId", mutableMapOf(
                AppserviceUser("someUserId1", false) to MemberOfProperties(12),
                AppserviceUser("someUserId2", true) to MemberOfProperties(24)
        )
        )
        val message = RoomMessage(
                room,
                "some body 1",
                Instant.ofEpochMilli(123),
                setOf("someUserId1", "someUserId2")
        )


        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), txnId = any()) }.returns("someId")

        runBlocking { cut.sendRoomMessage(message) }

        coVerifyAll {
            matrixClientMock.roomsApi.sendRoomEvent(
                    "someRoomId",
                    match<TextMessageEventContent> { it.body == "some body 1" },
                    txnId = any()
            )
        }
        verify {
            messageRepositoryMock.delete(message)
        }
    }

    @Test
    fun `should try to send and delete messages when sending fails`() {
        val room = AppserviceRoom(
                "someRoomId", mutableMapOf(
                AppserviceUser("someUserId1", false) to MemberOfProperties(12)
        )
        )
        val message = RoomMessage(room, "some body", Instant.ofEpochMilli(123))


        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), txnId = any()) }.throws(
                MatrixServerException(
                        FORBIDDEN,
                        ErrorResponse("M_FORBIDDEN")
                )
        )

        runBlocking { cut.sendRoomMessage(message) }

        coVerifyAll {
            matrixClientMock.roomsApi.sendRoomEvent(
                    "someRoomId",
                    match<TextMessageEventContent> { it.body == "some body" },
                    txnId = any()
            )
        }
        verify {
            messageRepositoryMock.delete(message)
        }
    }

    @Test
    fun `should try to send but not delete messages when sending fails unhandled`() {
        val room = AppserviceRoom(
                "someRoomId", mutableMapOf(
                AppserviceUser("someUserId1", false) to MemberOfProperties(12)
        )
        )
        val message = RoomMessage(room, "some body", Instant.ofEpochMilli(123))

        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), txnId = any()) }
                .throws(RuntimeException("woops"))

        runBlocking { cut.sendRoomMessage(message) }

        coVerify {
            matrixClientMock.roomsApi.sendRoomEvent(
                    "someRoomId",
                    match<TextMessageEventContent> { it.body == "some body" },
                    txnId = any()
            )
        }
        verify(exactly = 0) {
            messageRepositoryMock.delete(message)
        }
    }

    @Test
    fun `should not send messages when time is in future`() {
        val room = AppserviceRoom(
                "someRoomId", mutableMapOf(
                AppserviceUser("someUserId1", false) to MemberOfProperties(12)
        )
        )
        val message = RoomMessage(room, "some body", Instant.now().plusSeconds(2000))

        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), txnId = any()) }.returns("someId")

        runBlocking { cut.sendRoomMessage(message) }

        coVerify(exactly = 0) {
            matrixClientMock.roomsApi.sendRoomEvent(
                    "someRoomId",
                    match<TextMessageEventContent> { it.body == "some body" },
                    txnId = any()
            )
        }
        verify(exactly = 0) {
            messageRepositoryMock.delete(message)
        }
    }

    @Test
    fun `should not send messages when members are missing in room`() {
        val room = AppserviceRoom(
                "someRoomId", mutableMapOf(
                AppserviceUser("someUserId1", false) to MemberOfProperties(12)
        )
        )
        val message = RoomMessage(
                room,
                "some body",
                Instant.ofEpochMilli(123),
                setOf("someUserId1", "someUserId2")
        )

        coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), txnId = any()) }
                .throws(RuntimeException("woops"))

        runBlocking { cut.sendRoomMessage(message) }

        coVerify(exactly = 0) {
            matrixClientMock.roomsApi.sendRoomEvent(
                    "someRoomId",
                    match<TextMessageEventContent> { it.body == "some body" },
                    txnId = any()
            )
        }
        verify(exactly = 0) {
            messageRepositoryMock.delete(message)
        }
    }
}