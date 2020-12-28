package net.folivo.matrix.bridge.sms.handler

import com.google.i18n.phonenumbers.NumberParseException
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
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

    @Test
    fun `should detect alphanumeric numbers`() {
        cut.isAlphanumeric("DINODINO").shouldBeTrue()
        cut.isAlphanumeric("DINODINODINO").shouldBeFalse()
        cut.isAlphanumeric("123-DINO").shouldBeFalse()
        cut.isAlphanumeric("+49123456789").shouldBeFalse()
        cut.isAlphanumeric("0123456789").shouldBeFalse()
    }
}