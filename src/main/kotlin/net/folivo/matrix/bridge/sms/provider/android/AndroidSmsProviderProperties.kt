package net.folivo.matrix.bridge.sms.provider.android

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("matrix.bridge.sms.provider.android")
@ConstructorBinding
data class AndroidSmsProviderProperties(
        val enabled: Boolean = false,
        val baseUrl: String,
        val username: String,
        val password: String,
        val trustStore: TrustStore? = null
) {
    data class TrustStore(
            val path: String,
            val password: String,
            val type: String
    )
}