package net.folivo.matrix.bridge.sms.room

import io.mockk.Called
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.mockk.verifyOrder
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.AppserviceUserRepository
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.restclient.MatrixClient
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
    lateinit var matrixClient: MatrixClient

    @MockK
    lateinit var botProperties: MatrixBotProperties

    @MockK
    lateinit var smsBridgeProperties: SmsBridgeProperties

    @InjectMockKs
    lateinit var cut: SmsMatrixAppserviceRoomService

    @BeforeEach
    fun beforeEach() {

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
        val room = AppserviceRoom("someRoomId")
        val user = AppserviceUser("someUserId")
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
        every { appserviceUserRepositoryMock.findById("someUserId") }.returns(Mono.just(user))
        every { appserviceUserRepositoryMock.findLastMappingTokenByUserId("someUserId") }.returns(Mono.just(23))
        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))
        every { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }.returns(Mono.just(user))
        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))

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
        val room = AppserviceRoom("someRoomId")
        val user = AppserviceUser("someUserId")
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.empty())
        every { appserviceUserRepositoryMock.findById("someUserId") }.returns(Mono.empty())
        every { appserviceUserRepositoryMock.findLastMappingTokenByUserId("someUserId") }.returns(Mono.empty())
        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))
        every { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }.returns(Mono.just(user))


        every { appserviceRoomRepositoryMock.save<AppserviceRoom>(any()) }
                .returns(Mono.just(room))

        StepVerifier
                .create(cut.saveRoomJoin("someRoomId", "someUserId"))
                .verifyComplete()

        verifyOrder {
            appserviceRoomRepositoryMock.save<AppserviceRoom>(room)
            appserviceUserRepositoryMock.save<AppserviceUser>(match {
                it.rooms.containsKey(room)
                && it.rooms.containsValue(MemberOfProperties(1))
            })
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

    @Test
    fun `user should be member of room`() {
        val user = AppserviceUser("someUserId")
        val room = AppserviceRoom("someRoomId", mutableMapOf(user to MemberOfProperties(1)))
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))

        StepVerifier.create(cut.isMemberOf("someUserId", "someRoomId"))
                .assertNext { assertThat(it).isTrue() }
    }

    @Test
    fun `user should not be member of room`() {
        val user = AppserviceUser("someUserId")
        val room = AppserviceRoom("someRoomId", mutableMapOf(user to MemberOfProperties(1)))
        every { appserviceRoomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))

        StepVerifier.create(cut.isMemberOf("someOtherUserId", "someRoomId"))
                .assertNext { assertThat(it).isFalse() }
    }
}