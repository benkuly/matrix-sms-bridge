package net.folivo.matrix.bridge.sms.provider.android

data class AndroidSmsMessagesResponse(
        val nextBatch: Long,
        val messages: List<AndroidSmsMessage>
)