package net.folivo.matrix.bridge.sms.user

import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService.UserExistingState.*
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

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

        val result = runBlocking { cut.userExistingState("someUserId") }
        assertThat(result).isEqualTo(EXISTS)
    }

    @Test
    fun `userExistingState should be CAN_BE_CREATED when creation is allowed`() {
        every { appserviceUserRepositoryMock.existsById("someUserId") }
                .returns(Mono.just(false))

        coEvery { helperMock.isManagedUser("someUserId") }.returns(true)

        val result = runBlocking { cut.userExistingState("someUserId") }
        assertThat(result).isEqualTo(CAN_BE_CREATED)
    }

    @Test
    fun `userExistingState should be DOES_NOT_EXISTS when creation is not allowed`() {
        every { appserviceUserRepositoryMock.existsById("someUserId") }
                .returns(Mono.just(false))

        coEvery { helperMock.isManagedUser("someUserId") }.returns(false)

        val result = runBlocking { cut.userExistingState("someUserId") }
        assertThat(result).isEqualTo(DOES_NOT_EXISTS)
    }

    @Test
    fun `should save managed user in database`() {
        val user = AppserviceUser("someUserId", true)
        every { appserviceUserRepositoryMock.existsById(any<String>()) }
                .returns(Mono.just(false))
        every { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }
                .returns(Mono.just(user))
        coEvery { helperMock.isManagedUser("someUserId") }.returns(true)

        runBlocking { cut.saveUser("someUserId") }

        verify { appserviceUserRepositoryMock.save(user) }
    }

    @Test
    fun `should save not managed user in database`() {
        val user = AppserviceUser("someUserId", false)
        every { appserviceUserRepositoryMock.existsById(any<String>()) }
                .returns(Mono.just(true))
        every { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }
                .returns(Mono.just(user))
        coEvery { helperMock.isManagedUser("someUserId") }.returns(false)

        runBlocking { cut.saveUser("someUserId") }

        verify(exactly = 0) { appserviceUserRepositoryMock.save(user) }
    }

    @Test
    fun `should not save user if already exists`() {
        val user = AppserviceUser("someUserId", true)
        every { appserviceUserRepositoryMock.existsById(any<String>()) }
                .returns(Mono.just(true))
        every { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }
                .returns(Mono.just(user))
        coEvery { helperMock.isManagedUser("someUserId") }.returns(true)

        runBlocking { cut.saveUser("someUserId") }


        verify(exactly = 0) { appserviceUserRepositoryMock.save<AppserviceUser>(any()) }
    }

    @Test
    fun `should createUserParameter`() {
        val result = runBlocking { cut.getCreateUserParameter("@sms_1234567:someServer") }
        assertThat(result.displayName).isEqualTo("+1234567 (SMS)")
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
        val result = runBlocking { cut.getRoomId("someUserId", 24) }
        assertThat(result).isEqualTo("someRoomId2")
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
        val result = runBlocking { cut.getRoomId("someUserId", 12) }
        assertThat(result).isEqualTo("someRoomId1")

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
        val result = runBlocking { cut.getRoomId("someUserId", 24) }
        assertThat(result).isEqualTo("someRoomId1")

    }

    @Test
    fun `should not get roomId`() {
        every { appserviceUserRepositoryMock.findById("someUserId") }
                .returns(Mono.just(AppserviceUser("someUserId", true, mutableMapOf())))
        val result = runBlocking { cut.getRoomId("someUserId", 24) }
        assertThat(result).isNull()
    }
}