package net.folivo.matrix.bridge.sms.provider.android

import com.fasterxml.jackson.annotation.JsonProperty

data class AndroidSmsMessage(
        @JsonProperty("number")
        val sender: String,
        @JsonProperty("body")
        val body: String,
        @JsonProperty
        val id: Int,
)