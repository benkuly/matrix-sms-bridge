package net.folivo.matrix.bridge.sms.provider

import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

class NoOpSmsProvider : SmsProvider {
    private val logger = LoggerFactory.getLogger(NoOpSmsProvider::class.java)

    private val errorMessage = "A configured SmsProvider is missing. Please ensure, that you configured a SmsProvider in the configuration file."

    init {
        logger.error(errorMessage)
    }

    override fun sendSms(receiver: String, body: String): Mono<Void> {
        logger.error(errorMessage)
        return Mono.empty()
    }
}