package net.folivo.matrix.bridge.sms.provider.android

import com.fasterxml.jackson.annotation.JsonProperty

data class AndroidInSmsMessagesResponse(
        @JsonProperty("nextBatch")
        val nextBatch: Int,
        @JsonProperty("messages")
        val messages: List<AndroidInSmsMessage>
)