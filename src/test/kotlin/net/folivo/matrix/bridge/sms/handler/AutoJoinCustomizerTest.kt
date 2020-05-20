package net.folivo.matrix.bridge.sms.handler

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.test.StepVerifier

@ExtendWith(MockKExtension::class)
class AutoJoinCustomizerTest {
    private val cut = AutoJoinCustomizer(mockk {
        every { defaultRoomId }.returns("defaultRoomId")
    })

    @Test
    fun `should deny autojoin of application server user`() {
        StepVerifier
                .create(cut.shouldJoin("someRoomId", "someUserId", true))
                .assertNext { assertThat(it).isFalse() }
    }

    @Test
    fun `should allow autojoin of application server user to configured default room`() {
        StepVerifier
                .create(cut.shouldJoin("defaultRoomId", "someUserId", true))
                .assertNext { assertThat(it).isTrue() }
    }

    @Test
    fun `should allow autojoin of other users`() {
        StepVerifier
                .create(cut.shouldJoin("someRoomId", "someUserId", false))
                .assertNext { assertThat(it).isTrue() }
    }
}