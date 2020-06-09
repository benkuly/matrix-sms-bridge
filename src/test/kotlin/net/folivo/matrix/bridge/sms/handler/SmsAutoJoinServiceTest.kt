package net.folivo.matrix.bridge.sms.handler

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.AppserviceUserRepository
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class SmsAutoJoinServiceTest {
    @MockK
    lateinit var userRepositoryMock: AppserviceUserRepository

    @InjectMockKs
    lateinit var cut: SmsAutoJoinService

    @Test
    fun `should allow autojoin if user is saved in room`() {
        every { userRepositoryMock.findById("someUserId") }.returns(Mono.just(mockk<AppserviceUser> {
            every { rooms }.returns(mutableMapOf(AppserviceRoom("someRoomId") to MemberOfProperties(24)))
        }))
        StepVerifier
                .create(cut.shouldJoin("someRoomId", "someUserId"))
                .assertNext { assertThat(it).isTrue() }
                .verifyComplete()
    }

    @Test
    fun `should not allow autojoin if user is not saved in room`() {
        every { userRepositoryMock.findById("someUserId") }.returns(Mono.just(mockk<AppserviceUser> {
            every { rooms }.returns(mutableMapOf(AppserviceRoom("someOtherRoomId") to MemberOfProperties(24)))
        }))
        StepVerifier
                .create(cut.shouldJoin("someRoomId", "someUserId"))
                .assertNext { assertThat(it).isFalse() }
                .verifyComplete()
    }
}