package net.folivo.matrix.bridge.sms.provider.android

import io.netty.handler.ssl.SslContextBuilder
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.ReceiveSmsService
import net.folivo.matrix.bridge.sms.provider.PhoneNumberService
import net.folivo.matrix.restclient.MatrixClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.nio.file.Files
import java.nio.file.Path
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
        val builder = webClientBuilder
            .baseUrl(properties.baseUrl)
            .defaultHeader(
                HttpHeaders.AUTHORIZATION,
                "Basic " + Base64.getEncoder()
                    .encodeToString("${properties.username}:${properties.password}".toByteArray())
            )
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .filter(ExchangeFilterFunction.ofResponseProcessor { clientResponse: ClientResponse ->
                val statusCode = clientResponse.statusCode()
                if (clientResponse.statusCode().isError) {
                    clientResponse.bodyToMono(String::class.java)
                        .flatMap {
                            Mono.error(AndroidSmsProviderException(it, statusCode))
                        }
                } else {
                    Mono.just(clientResponse)
                }
            })

        val trustStoreProps = properties.trustStore
        if (trustStoreProps != null) {
            val keyStore = KeyStore.getInstance(trustStoreProps.type)
            keyStore.load(
                Files.newInputStream(Path.of(trustStoreProps.path)),
                trustStoreProps.password.toCharArray()
            )
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(keyStore)
            val sslContext = SslContextBuilder.forClient()
                .trustManager(factory)
                .build()
            val client = HttpClient.create().secure { spec -> spec.sslContext(sslContext) }
            builder.clientConnector(ReactorClientHttpConnector(client))
        }

        return builder.build();
    }

    @Bean
    fun androidSmsProvider(
        receiveSmsService: ReceiveSmsService,
        phoneNumberService: PhoneNumberService,
        processedRepository: AndroidSmsProcessedRepository,
        outSmsMessageRepository: AndroidOutSmsMessageRepository,
        @Qualifier("androidSmsProviderWebClient")
        webClient: WebClient,
        matrixClient: MatrixClient,
        smsBridgeProperties: SmsBridgeProperties
    ): AndroidSmsProvider {
        return AndroidSmsProvider(
            receiveSmsService,
            phoneNumberService,
            processedRepository,
            outSmsMessageRepository,
            webClient,
            matrixClient,
            smsBridgeProperties
        )
    }

    @Bean
    fun smsProviderLauncher(
        androidSmsProvider: AndroidSmsProvider,
        smsBridgeProperties: SmsBridgeProperties,
        matrixClient: MatrixClient
    ): AndroidSmsProviderLauncher {
        return AndroidSmsProviderLauncher(androidSmsProvider, smsBridgeProperties, matrixClient)
    }
}