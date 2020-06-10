package net.folivo.matrix.bridge.sms.room

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
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
    lateinit var matrixClientMock: MatrixClient

    @InjectMockKs
    lateinit var cut: SmsMatrixAppserviceRoomService

    @BeforeEach
    fun beforeEach() {
        every { helperMock.isManagedUser("someUserId") }.returns(Mono.just(true))
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
            appserviceUserRepositoryMock.save<AppserviceUser>(match {
                it.rooms.contains(room)
                && it.rooms.containsValue(MemberOfProperties(24))
            })
        }
    }

    @Test
    fun `should save user join to room in database even if entities does not exists`() {
        val user = AppserviceUser("someUserId", true)
        val roomWithoutMember = AppserviceRoom("someRoomId")
        val roomWithMember = AppserviceRoom("someRoomId", mutableMapOf(user to mockk()))
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returnsMany(
                Mono.empty(),
                Mono.just(roomWithMember)
        )
        every { appserviceUserRepositoryMock.findById(any<String>()) }.returns(Mono.empty())
        every { appserviceUserRepositoryMock.findLastMappingTokenByUserId(any()) }.returns(Mono.empty())
        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(roomWithoutMember))
        every { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }.returns(Mono.just(user))
        every { matrixClientMock.roomsApi.getJoinedMembers(allAny()) }.returns(
                Mono.just(
                        GetJoinedMembersResponse(
                                mapOf(
                                        "someExistingUserId" to mockk()
                                )
                        )
                )
        )
        every { helperMock.isManagedUser("someExistingUserId") }.returns(Mono.just(false))

        StepVerifier
                .create(cut.saveRoomJoin("someRoomId", "someUserId"))
                .verifyComplete()

        verifyOrder {
            appserviceRoomRepositoryMock.save<AppserviceRoom>(roomWithoutMember)
            appserviceUserRepositoryMock.save<AppserviceUser>(match {
                it.rooms.containsKey(roomWithoutMember)
                && it.rooms.containsValue(MemberOfProperties(1))
            })
            appserviceUserRepositoryMock.save<AppserviceUser>(match {
                it.userId == "someExistingUserId"
                && it.rooms.containsKey(roomWithMember)
                && !it.isManaged
                && it.rooms.containsValue(MemberOfProperties(1))
            })
        }
    }

    @Test
    fun `should save user room leave in database`() {
        val room1 = AppserviceRoom("someRoomId1")
        val room2 = AppserviceRoom("someRoomId2")
        val user = AppserviceUser(
                "someUserId", true, mutableMapOf(
                room1 to MemberOfProperties(1), room2 to MemberOfProperties(1)
        )
        )
        every { appserviceUserRepositoryMock.findById("someUserId") }.returns(Mono.just(user))
        every { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }.returns(Mono.just(user))

        StepVerifier
                .create(cut.saveRoomLeave("someRoomId1", "someUserId"))
                .verifyComplete()

        verify { appserviceUserRepositoryMock.save<AppserviceUser>(match { it.rooms.contains(room2) }) }
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