package net.folivo.matrix.bridge.sms.room

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.verifyOrder
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.AppserviceUserRepository
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class SmsMatrixAppserviceRoomServiceTest {

    @MockK
    lateinit var matrixAppserviceServiceHelperMock: MatrixAppserviceServiceHelper

    @MockK
    lateinit var appserviceRoomRepositoryMock: AppserviceRoomRepository

    @MockK
    lateinit var appserviceUserRepositoryMock: AppserviceUserRepository

    @InjectMockKs
    lateinit var cut: SmsMatrixAppserviceRoomService

    @Test
    fun `roomExistingState should be DOES_NOT_EXIST when room is in database`() {
        StepVerifier
                .create(cut.roomExistingState("someRoomAlias"))
                .assertNext { assertThat(it).isEqualTo(DOES_NOT_EXISTS) }
                .verifyComplete()
    }

    @Test
    fun `should save room in database`() {
        val room = AppserviceRoom("someRoomId", "someRoomAlias")
        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }
                .returns(Mono.just(room))

        StepVerifier
                .create(cut.saveRoom("someRoomAlias", "someRoomId"))
                .verifyComplete()

        verify { appserviceRoomRepositoryMock.save(room) }
    }

    @Test
    fun `should save user join to room in database`() {
        val room = AppserviceRoom("someRoomId", "someRoomAlias")
        val user = AppserviceUser("someUserId")
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
        every { appserviceUserRepositoryMock.findById("someUserId") }.returns(Mono.just(user))
        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))
        every { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }.returns(Mono.just(user))

        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }
                .returns(Mono.just(room))

        StepVerifier
                .create(cut.saveRoomJoin("someRoomId", "someUserId"))
                .verifyComplete()

        verify { appserviceUserRepositoryMock.save<AppserviceUser>(match { it.rooms.contains(room) }) }
    }

    @Test
    fun `should save user join to room in database even if entities does not exists`() {
        val room = AppserviceRoom("someRoomId")
        val user = AppserviceUser("someUserId")
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.empty())
        every { appserviceUserRepositoryMock.findById("someUserId") }.returns(Mono.empty())
        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))
        every { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }.returns(Mono.just(user))

        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }
                .returns(Mono.just(room))

        StepVerifier
                .create(cut.saveRoomJoin("someRoomId", "someUserId"))
                .verifyComplete()

        verifyOrder {
            appserviceRoomRepositoryMock.save<AppserviceRoom>(room)
            appserviceUserRepositoryMock.save<AppserviceUser>(match { it.rooms.contains(room) })
        }
    }

    @Test
    fun `should save user room leave in database`() {
        val room1 = AppserviceRoom("someRoomId1")
        val room2 = AppserviceRoom("someRoomId2")
        val user = AppserviceUser(
                "someUserId", mutableMapOf(
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
}