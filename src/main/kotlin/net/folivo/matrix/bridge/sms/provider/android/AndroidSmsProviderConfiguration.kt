package net.folivo.matrix.bridge.sms.provider.android

import net.folivo.matrix.bridge.sms.handler.ReceiveSmsService
import net.folivo.matrix.bridge.sms.provider.PhoneNumberService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

//FIXME test
@Profile("!initialsync")
@Configuration
@ConditionalOnProperty(prefix = "matrix.bridge.sms.provider.android", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(AndroidSmsProviderProperties::class)
class AndroidSmsProviderConfiguration(private val properties: AndroidSmsProviderProperties) {
    @Bean
    fun androidSmsProviderWebClient(webClientBuilder: WebClient.Builder): WebClient {
        return webClientBuilder
                .baseUrl(properties.basePath)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Basic " + Base64.getEncoder()
                                .encodeToString("${properties.username}:${properties.password}".toByteArray())
                )
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    fun androidSmsProvider(
            receiveSmsService: ReceiveSmsService,
            phoneNumberService: PhoneNumberService,
            batchRepository: AndroidSmsBatchRepository,
            @Qualifier("androidSmsProviderWebClient")
            webClient: WebClient
    ): AndroidSmsProvider {
        return AndroidSmsProvider(receiveSmsService, phoneNumberService, batchRepository, webClient)
    }
}