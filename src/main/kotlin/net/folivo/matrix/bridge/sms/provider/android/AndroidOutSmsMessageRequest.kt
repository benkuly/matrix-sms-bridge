package net.folivo.matrix.bridge.sms.provider.android

import com.fasterxml.jackson.annotation.JsonProperty

data class AndroidOutSmsMessageRequest(
    @JsonProperty("recipientPhoneNumber")
    val receiver: String,
    @JsonProperty("message")
    val body: String
)