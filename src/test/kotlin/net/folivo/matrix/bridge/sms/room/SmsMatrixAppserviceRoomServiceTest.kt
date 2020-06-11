package net.folivo.matrix.bridge.sms.room

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.AppserviceUserRepository
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.restclient.MatrixClient
import net.folivo.matrix.restclient.api.rooms.GetJoinedMembersResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class SmsMatrixAppserviceRoomServiceTest {

    @MockK
    lateinit var appserviceRoomRepositoryMock: AppserviceRoomRepository

    @MockK
    lateinit var appserviceUserRepositoryMock: AppserviceUserRepository

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
        every { helperMock.isManagedUser("someUserId") }.returns(Mono.just(true))
        every { botPropertiesMock.username }.returns("bot")
        every { botPropertiesMock.serverName }.returns("someServer")
    }

    @Test
    fun `roomExistingState should always be DOES_NOT_EXIST`() {
        StepVerifier
                .create(cut.roomExistingState("someRoomAlias"))
                .assertNext { assertThat(it).isEqualTo(DOES_NOT_EXISTS) }
                .verifyComplete()
    }

    @Test
    fun `should not save room in database`() {
        StepVerifier
                .create(cut.saveRoom("someRoomAlias", "someRoomId"))
                .verifyComplete()

        verify { appserviceRoomRepositoryMock wasNot Called }
    }

    @Test
    fun `should save user join to room in database`() {
        val room = AppserviceRoom("someRoomId", mutableMapOf(mockk<AppserviceUser>() to mockk()))
        val user = AppserviceUser("someUserId", true)
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
        every { appserviceUserRepositoryMock.findById("someUserId") }.returns(Mono.just(user))
        every { appserviceUserRepositoryMock.findLastMappingTokenByUserId("someUserId") }.returns(Mono.just(23))
        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))
        every { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }.returns(Mono.just(user))

        StepVerifier
                .create(cut.saveRoomJoin("someRoomId", "someUserId"))
                .verifyComplete()

        verify {
            appserviceRoomRepositoryMock.save<AppserviceRoom>(match {
                it.members.contains(user)
                && it.members[user] == MemberOfProperties(24)
            })
        }
    }

    @Test
    fun `should save user join to room in database even if entities does not exists`() {
        val user = AppserviceUser("someUserId", true)
        val existingUser = AppserviceUser("someExistingUserId", false)
        val roomWithoutMember = AppserviceRoom("someRoomId")
        val roomWithMember = AppserviceRoom(
                "someRoomId", mutableMapOf(
                existingUser to MemberOfProperties(1),
                user to MemberOfProperties(1)
        )
        )
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.empty())
        every { appserviceUserRepositoryMock.findById(any<String>()) }.returns(Mono.empty())
        every { appserviceUserRepositoryMock.findLastMappingTokenByUserId(any()) }.returns(Mono.empty())
        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(roomWithoutMember))
        every { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }.returnsMany(
                Mono.just(existingUser), Mono.just(user)
        )
        every { matrixClientMock.roomsApi.getJoinedMembers(allAny()) }.returns(
                Mono.just(
                        GetJoinedMembersResponse(
                                mapOf(
                                        "someExistingUserId" to mockk(),
                                        "soneUserId" to mockk()
                                )
                        )
                )
        )
        every { helperMock.isManagedUser("someExistingUserId") }.returns(Mono.just(false))
        every { helperMock.isManagedUser("soneUserId") }.returns(Mono.just(true))


        StepVerifier
                .create(cut.saveRoomJoin("someRoomId", "someUserId"))
                .verifyComplete()

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

        StepVerifier
                .create(cut.saveRoomLeave("someRoomId", "someUserId1"))
                .verifyComplete()

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
        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))
        every { matrixClientMock.roomsApi.leaveRoom(allAny()) }.returns(Mono.empty())

        StepVerifier
                .create(cut.saveRoomLeave("someRoomId", "someUserId1"))
                .verifyComplete()

        verifyAll {
            appserviceRoomRepositoryMock.findById("someRoomId")
            matrixClientMock.roomsApi.leaveRoom("someRoomId", "someUserId2")
            matrixClientMock.roomsApi.leaveRoom("someRoomId", null)
            appserviceRoomRepositoryMock.delete(match {
                it.members.keys.containsAll(listOf(user2, user3))
            })
        }
    }

    @Test
    fun `user should be member of room`() {
        val user = AppserviceUser("someUserId", true)
        val room = AppserviceRoom("someRoomId", mutableMapOf(user to MemberOfProperties(1)))
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))

        StepVerifier.create(cut.isMemberOf("someUserId", "someRoomId"))
                .assertNext { assertThat(it).isTrue() }
    }

    @Test
    fun `user should not be member of room`() {
        val user = AppserviceUser("someUserId", true)
        val room = AppserviceRoom("someRoomId", mutableMapOf(user to MemberOfProperties(1)))
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))

        StepVerifier.create(cut.isMemberOf("someOtherUserId", "someRoomId"))
                .assertNext { assertThat(it).isFalse() }
    }
}