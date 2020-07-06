package net.folivo.matrix.bridge.sms.room

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.bridge.sms.user.SmsMatrixAppserviceUserService
import net.folivo.matrix.restclient.MatrixClient
import net.folivo.matrix.restclient.api.rooms.GetJoinedMembersResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

@ExtendWith(MockKExtension::class)
class SmsMatrixAppserviceRoomServiceTest {

    @MockK
    lateinit var appserviceRoomRepositoryMock: AppserviceRoomRepository

    @MockK
    lateinit var userServiceMock: SmsMatrixAppserviceUserService

    @MockK
    lateinit var helperMock: MatrixAppserviceServiceHelper

    @MockK
    lateinit var botPropertiesMock: MatrixBotProperties

    @MockK
    lateinit var matrixClientMock: MatrixClient

    @InjectMockKs
    lateinit var cut: SmsMatrixAppserviceRoomService

    @BeforeEach
    fun beforeEach() {
        coEvery { helperMock.isManagedUser("someUserId") }.returns(true)
        every { botPropertiesMock.username }.returns("bot")
        every { botPropertiesMock.serverName }.returns("someServer")
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
        verify { appserviceRoomRepositoryMock wasNot Called }
    }

    @Test
    fun `should save user join to room in database`() {
        val room = AppserviceRoom("someRoomId", mutableMapOf(mockk<AppserviceUser>() to mockk()))
        val user = AppserviceUser("someUserId", true)
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
        coEvery { userServiceMock.getUser("someUserId") }.returns(user)
        coEvery { userServiceMock.getLastMappingToken("someUserId") }.returns(23)
        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))

        runBlocking {
            cut.saveRoomJoin("someRoomId", "someUserId")
        }

        verify {
            appserviceRoomRepositoryMock.save<AppserviceRoom>(match {
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
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.empty())
        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(roomWithoutMember))
        coEvery { matrixClientMock.roomsApi.getJoinedMembers(allAny()) }.returns(
                GetJoinedMembersResponse(
                        mapOf(
                                "someExistingUserId" to mockk(),
                                "someUserId" to mockk()
                        )
                )
        )
        coEvery { helperMock.isManagedUser("someExistingUserId") }.returns(false)

        runBlocking {
            cut.saveRoomJoin("someRoomId", "someUserId")
        }

        verify {
            appserviceRoomRepositoryMock.save<AppserviceRoom>(roomWithMember)
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
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))

        runBlocking {
            cut.saveRoomLeave("someRoomId", "someUserId1")
        }

        verify {
            appserviceRoomRepositoryMock.save<AppserviceRoom>(match {
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
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
        every { appserviceRoomRepositoryMock.delete(any()) }.returns(Mono.empty())
        coEvery { matrixClientMock.roomsApi.leaveRoom(allAny()) } just Runs

        runBlocking {
            cut.saveRoomLeave("someRoomId", "someUserId1")
        }

        verifyAll {
            appserviceRoomRepositoryMock.findById("someRoomId")
            appserviceRoomRepositoryMock.delete(match {
                it.members.keys.containsAll(listOf(user2, user3))
            })
        }
        coVerifyAll {
            matrixClientMock.roomsApi.leaveRoom("someRoomId", "someUserId2")
            matrixClientMock.roomsApi.leaveRoom("someRoomId", null)
        }
    }

//    @Test//FIXME old?
//    fun `user should be member of room`() {
//        val user = AppserviceUser("someUserId", true)
//        val room = AppserviceRoom("someRoomId", mutableMapOf(user to MemberOfProperties(1)))
//        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
//
//        val result = runBlocking { cut.isMemberOf("someUserId", "someRoomId") }
//        assertThat(result).isTrue()
//    }
//
//    @Test
//    fun `user should not be member of room`() {
//        val user = AppserviceUser("someUserId", true)
//        val room = AppserviceRoom("someRoomId", mutableMapOf(user to MemberOfProperties(1)))
//        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
//
//        StepVerifier.create(cut.isMemberOf("someOtherUserId", "someRoomId"))
//        assertThat(result).isFalse()
//    }
}