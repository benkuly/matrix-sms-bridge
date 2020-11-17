package net.folivo.matrix.bridge.sms.provider.android

import io.netty.handler.ssl.SslContextBuilder
import net.folivo.matrix.bridge.sms.handler.ReceiveSmsService
import net.folivo.matrix.bridge.sms.provider.PhoneNumberService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ClientHttpConnector
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.security.KeyStore
import java.util.*
import javax.net.ssl.TrustManagerFactory


@Profile("!initialsync")
@Configuration
@ConditionalOnProperty(prefix = "matrix.bridge.sms.provider.android", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(AndroidSmsProviderProperties::class)
class AndroidSmsProviderConfiguration(private val properties: AndroidSmsProviderProperties) {

    @Bean("androidSmsProviderWebClient")
    fun androidSmsProviderWebClient(webClientBuilder: WebClient.Builder): WebClient {
        val trustStoreProps = properties.trustStore
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(
                javaClass.classLoader.getResourceAsStream(trustStoreProps.path),
                trustStoreProps.password.toCharArray()
        )
        val factory = TrustManagerFactory.getInstance(trustStoreProps.type);
        factory.init(keyStore);
        val sslContext = SslContextBuilder.forClient()
                .trustManager(factory)
                .build()
        val client: HttpClient = HttpClient.create().secure { spec -> spec.sslContext(sslContext) }
        val connector: ClientHttpConnector = ReactorClientHttpConnector(client)
        return webClientBuilder
                .baseUrl(properties.baseUrl)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Basic " + Base64.getEncoder()
                                .encodeToString("${properties.username}:${properties.password}".toByteArray())
                )
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(connector)
                .build();
    }

    @Bean
    @Primary
    fun androidSmsProvider(
            receiveSmsService: ReceiveSmsService,
            phoneNumberService: PhoneNumberService,
            processedRepository: AndroidSmsProcessedRepository,
            @Qualifier("androidSmsProviderWebClient")
            webClient: WebClient
    ): AndroidSmsProvider {
        return AndroidSmsProvider(receiveSmsService, phoneNumberService, processedRepository, webClient)
    }

    @Bean
    fun smsProviderLauncher(androidSmsProvider: AndroidSmsProvider): AndroidSmsProviderLauncher {
        return AndroidSmsProviderLauncher(androidSmsProvider)
    }
}