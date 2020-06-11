package net.folivo.matrix.bridge.sms.user

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService.UserExistingState.*
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class SmsMatrixAppserviceUserServiceTest {
    @MockK
    lateinit var helperMock: MatrixAppserviceServiceHelper

    @MockK
    lateinit var appserviceUserRepositoryMock: AppserviceUserRepository

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @InjectMockKs
    lateinit var cut: SmsMatrixAppserviceUserService

    @Test
    fun `userExistingState should be EXISTS when user is in database`() {
        every { appserviceUserRepositoryMock.existsById("someUserId") }
                .returns(Mono.just(true))

        StepVerifier
                .create(cut.userExistingState("someUserId"))
                .assertNext { Assertions.assertThat(it).isEqualTo(EXISTS) }
                .verifyComplete()
    }

    @Test
    fun `userExistingState should be CAN_BE_CREATED when creation is allowed`() {
        every { appserviceUserRepositoryMock.existsById("someUserId") }
                .returns(Mono.just(false))

        every { helperMock.isManagedUser("someUserId") }
                .returns(Mono.just(true))

        StepVerifier
                .create(cut.userExistingState("someUserId"))
                .assertNext { Assertions.assertThat(it).isEqualTo(CAN_BE_CREATED) }
                .verifyComplete()
    }

    @Test
    fun `userExistingState should be DOES_NOT_EXISTS when creation is not allowed`() {
        every { appserviceUserRepositoryMock.existsById("someUserId") }
                .returns(Mono.just(false))

        every { helperMock.isManagedUser("someUserId") }
                .returns(Mono.just(false))

        StepVerifier
                .create(cut.userExistingState("someUserId"))
                .assertNext { Assertions.assertThat(it).isEqualTo(DOES_NOT_EXISTS) }
                .verifyComplete()
    }

    @Test
    fun `should save managed user in database`() {
        val user = AppserviceUser("someUserId", true)
        every { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }
                .returns(Mono.just(user))
        every { helperMock.isManagedUser("someUserId") }.returns(Mono.just(true))

        StepVerifier
                .create(cut.saveUser("someUserId"))
                .verifyComplete()

        verify { appserviceUserRepositoryMock.save(user) }
    }

    @Test
    fun `should save not managed user in database`() {
        val user = AppserviceUser("someUserId", false)
        every { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }
                .returns(Mono.just(user))
        every { helperMock.isManagedUser("someUserId") }.returns(Mono.just(false))

        StepVerifier
                .create(cut.saveUser("someUserId"))
                .verifyComplete()

        verify { appserviceUserRepositoryMock.save(user) }
    }

    @Test
    fun `should createUserParameter`() {
        StepVerifier
                .create(cut.getCreateUserParameter("@sms_1234567:someServer"))
                .assertNext { assertThat(it.displayName).isEqualTo("+1234567 (SMS)") }
                .verifyComplete()
    }

    @Test
    fun `should get roomId`() {
        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)
        every { appserviceUserRepositoryMock.findById("someUserId") }
                .returns(
                        Mono.just(
                                AppserviceUser(
                                        "someUserId", true, mutableMapOf(
                                        AppserviceRoom("someRoomId1") to MemberOfProperties(12),
                                        AppserviceRoom("someRoomId2") to MemberOfProperties(24)
                                )
                                )
                        )
                )
        StepVerifier
                .create(cut.getRoomId("someUserId", 24))
                .assertNext { assertThat(it).isEqualTo("someRoomId2") }
                .verifyComplete()
    }

    @Test
    fun `should get roomId when mapping token forced`() {
        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)
        every { appserviceUserRepositoryMock.findById("someUserId") }
                .returns(
                        Mono.just(
                                AppserviceUser(
                                        "someUserId", true, mutableMapOf(
                                        AppserviceRoom("someRoomId1") to MemberOfProperties(12)
                                )
                                )
                        )
                )
        StepVerifier
                .create(cut.getRoomId("someUserId", 12))
                .assertNext { assertThat(it).isEqualTo("someRoomId1") }
                .verifyComplete()
    }

    @Test
    fun `should get first roomId when mapping token can be ignored`() {
        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(true)
        every { appserviceUserRepositoryMock.findById("someUserId") }
                .returns(
                        Mono.just(
                                AppserviceUser(
                                        "someUserId", true, mutableMapOf(
                                        AppserviceRoom("someRoomId1") to MemberOfProperties(12)
                                )
                                )
                        )
                )
        StepVerifier
                .create(cut.getRoomId("someUserId", 24))
                .assertNext { assertThat(it).isEqualTo("someRoomId1") }
                .verifyComplete()
    }

    @Test
    fun `should not get roomId`() {
        every { appserviceUserRepositoryMock.findById("someUserId") }
                .returns(Mono.just(AppserviceUser("someUserId", true, mutableMapOf())))
        StepVerifier
                .create(cut.getRoomId("someUserId", 24))
                .verifyComplete()
    }
}