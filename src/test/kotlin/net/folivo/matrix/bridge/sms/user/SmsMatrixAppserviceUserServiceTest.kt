package net.folivo.matrix.bridge.sms.user

import io.mockk.Called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.appservice.api.user.MatrixAppserviceUserService.UserExistingState.*
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import net.folivo.matrix.bot.config.MatrixBotProperties
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
    lateinit var botPropertiesMock: MatrixBotProperties

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
    fun `should not save user to prevent concurrency transactions`() {
        runBlocking { cut.saveUser("someUserId") }

        verify { appserviceUserRepositoryMock wasNot Called }
    }

    @Test
    fun `should createUserParameter for managed user`() {
        every { botPropertiesMock.username }.returns("bot")
        every { botPropertiesMock.serverName }.returns("someServer")

        val result = runBlocking { cut.getCreateUserParameter("@sms_1234567:someServer") }
        assertThat(result.displayName).isEqualTo("+1234567 (SMS)")
    }

    @Test
    fun `should createUserParameter for bot user`() {
        every { botPropertiesMock.username }.returns("bot")
        every { botPropertiesMock.serverName }.returns("someServer")

        val result = runBlocking { cut.getCreateUserParameter("@bot:someServer") }
        assertThat(result.displayName).isEqualTo("SMS Bot")
    }
}