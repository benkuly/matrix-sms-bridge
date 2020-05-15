package net.folivo.matrix.bridge.sms.provider

import net.folivo.matrix.bridge.sms.mapping.ReceiveSmsService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@RestController
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "matrix.bridge.sms.provider.kannel", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(KannelSmsProviderProperties::class)
class KannelSmsProvider(
        private val properties: KannelSmsProviderProperties,
        private val receiveSmsService: ReceiveSmsService
) : SmsProvider {

    private val logger = LoggerFactory.getLogger(KannelSmsProvider::class.java)

    init {
        logger.info("Using Kannel as SmsProvider.")
    }

    private val webClient = WebClient
            .builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

    override fun sendSms(receiver: String, body: String): Mono<Void> {
        return webClient.get().uri(properties.sendBaseUrl) {
            it.apply {
                queryParam("username", properties.username)
                queryParam("password", properties.password)
                queryParam("to", receiver)
                queryParam("text", body)

            }.build()
        }.exchange()
                .doOnError {
                    logger.error("could not send message to kannel: ${it.message}")
                    logger.debug(it.stackTrace.contentToString())
                }
                .then()
    }

    @GetMapping("/provider/kamel-receive")
    fun receiveSms(@RequestParam("sender") sender: String, @RequestParam("body") body: String): Mono<Void> {
        return receiveSmsService.receiveSms(body = body, sender = sender)
                .flatMap { sendSms(receiver = sender, body = it) }
    }
}