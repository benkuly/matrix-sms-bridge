package net.folivo.matrix.bridge.sms.provider.android

import org.springframework.http.HttpStatus

class AndroidSmsProviderException(message: String?, val status: HttpStatus) : Throwable(message)