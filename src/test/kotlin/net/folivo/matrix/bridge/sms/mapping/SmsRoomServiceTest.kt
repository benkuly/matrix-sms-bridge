package net.folivo.matrix.bridge.sms.mapping

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import net.folivo.matrix.bot.appservice.room.AppserviceRoom
import net.folivo.matrix.bot.appservice.room.AppserviceRoomRepository
import net.folivo.matrix.bot.appservice.user.AppserviceUser
import net.folivo.matrix.bot.appservice.user.AppserviceUserRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class SmsRoomServiceTest {

    @MockK
    lateinit var smsRoomRepositoryMock: SmsRoomRepository

    @MockK
    lateinit var appserviceRoomRepositoryMock: AppserviceRoomRepository

    @MockK
    lateinit var appserviceUserRepositoryMock: AppserviceUserRepository

    @InjectMockKs
    lateinit var cut: SmsRoomService

    @Test
    fun `should return SmsRoom`() {
        val smsRoom = mockk<SmsRoom>()
        every { smsRoomRepositoryMock.findByRoomIdAndUserId("someRoomId", "someUserId") }
                .returns(Mono.just(smsRoom))
        StepVerifier
                .create(cut.getBridgedSmsRoom("someRoomId", "someUserId"))
                .expectNext(smsRoom)
                .verifyComplete()
    }

    @Test
    fun `should create new SmsRoom and increase mapping token when no SmsRoom exists`() {
        val room = mockk<AppserviceRoom>()
        val user = mockk<AppserviceUser>()
        val smsRoom = mockk<SmsRoom>()
        every { smsRoomRepositoryMock.findByRoomIdAndUserId("someRoomId", "someUserId") }
                .returns(Mono.empty())
        every { smsRoomRepositoryMock.save<SmsRoom>(any()) }
                .returns(Mono.just(smsRoom))
        every { smsRoomRepositoryMock.findLastMappingTokenByUserId("someUserId") }
                .returns(Mono.just(2))
        every { appserviceRoomRepositoryMock.findById("someRoomId") }
                .returns(Mono.just(room))
        every { appserviceUserRepositoryMock.findById("someUserId") }
                .returns(Mono.just(user))

        StepVerifier
                .create(cut.getBridgedSmsRoom("someRoomId", "someUserId"))
                .expectNext(smsRoom)
                .verifyComplete()

        verify { smsRoomRepositoryMock.save<SmsRoom>(SmsRoom(3, room, user)) }
    }

    @Test
    fun `should create SmsRoom with new mapping token when no mapping token found`() {
        val room = mockk<AppserviceRoom>()
        val user = mockk<AppserviceUser>()
        val smsRoom = mockk<SmsRoom>()
        every { smsRoomRepositoryMock.findByRoomIdAndUserId("someRoomId", "someUserId") }
                .returns(Mono.empty())
        every { smsRoomRepositoryMock.save<SmsRoom>(any()) }
                .returns(Mono.just(smsRoom))
        every { smsRoomRepositoryMock.findLastMappingTokenByUserId("someUserId") }
                .returns(Mono.empty())
        every { appserviceRoomRepositoryMock.findById("someRoomId") }
                .returns(Mono.just(room))
        every { appserviceUserRepositoryMock.findById("someUserId") }
                .returns(Mono.just(user))

        StepVerifier
                .create(cut.getBridgedSmsRoom("someRoomId", "someUserId"))
                .expectNext(smsRoom)
                .verifyComplete()

        verify { smsRoomRepositoryMock.save<SmsRoom>(SmsRoom(1, room, user)) }
    }
}