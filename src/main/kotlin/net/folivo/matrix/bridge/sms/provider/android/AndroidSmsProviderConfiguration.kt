package net.folivo.matrix.bridge.sms.provider.android

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.util.*

@Configuration
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
}