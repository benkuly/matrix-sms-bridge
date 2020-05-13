package net.folivo.matrix.bridge.sms.mapping

import net.folivo.matrix.appservice.api.user.CreateUserParameter
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier

class SmsBridgeAppserviceManagerTest {

    private val cut = SmsBridgeAppserviceManager()

    @Test
    fun `should set username`() {
        StepVerifier.create(cut.getCreateUserParameter("@sms_+123456789:someServer"))
                .expectNext(CreateUserParameter(displayName = "+123456789 (SMS)"))
    }
}