package net.folivo.matrix.bridge.sms.handler

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.NumberParseException.ErrorType.NOT_A_NUMBER
import com.google.i18n.phonenumbers.PhoneNumberUtil
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import org.springframework.stereotype.Service

@Service
class PhoneNumberService(private val smsBridgeProperties: SmsBridgeProperties) {

    private val phoneNumberUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    private val alphanumericRegex = "^(?=.*[a-zA-Z])(?=.*[a-zA-Z0-9])([a-zA-Z0-9 ]{1,11})\$".toRegex()

    fun parseToInternationalNumber(raw: String): String {
        return phoneNumberUtil.parse(raw, smsBridgeProperties.defaultRegion)
            .let {
                if (!phoneNumberUtil.isValidNumber(it)) throw NumberParseException(
                    NOT_A_NUMBER,
                    "not a valid number"
                )
                "+${it.countryCode}${it.nationalNumber}"
            }
    }

    fun isAlphanumeric(raw: String): Boolean {
        return alphanumericRegex.matches(raw)
    }
}