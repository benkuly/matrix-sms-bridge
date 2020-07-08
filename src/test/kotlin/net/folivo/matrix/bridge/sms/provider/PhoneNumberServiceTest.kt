package net.folivo.matrix.bridge.sms.provider

import com.google.i18n.phonenumbers.NumberParseException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail

@ExtendWith(MockKExtension::class)
class PhoneNumberServiceTest {

    @MockK
    lateinit var smsBridgePropertiesMock: SmsBridgeProperties

    @InjectMockKs
    lateinit var cut: PhoneNumberService

    @BeforeEach
    fun beforeEach() {
        every { smsBridgePropertiesMock.defaultRegion }.returns("DE")
    }

    @Test
    fun `should fail and echo when wrong telephone number`() {
        try {
            cut.parseToInternationalNumber("abc")
            cut.parseToInternationalNumber("123456789123456789")
            cut.parseToInternationalNumber("012345678 DINO")
            fail { "should have error" }
        } catch (error: NumberParseException) {

        }
    }
}