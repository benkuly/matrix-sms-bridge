package net.folivo.matrix.bridge.sms.provider

interface SmsProvider {
    suspend fun sendSms(receiver: String, body: String)
}