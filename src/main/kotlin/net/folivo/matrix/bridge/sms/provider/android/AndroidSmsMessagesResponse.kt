package net.folivo.matrix.bridge.sms.provider.android

data class AndroidSmsMessagesResponse(
        val nextBatch: String,
        val messages: List<AndroidSmsMessage>
)