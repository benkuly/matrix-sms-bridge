package net.folivo.matrix.bridge.sms.provider

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("matrix.bridge.sms.provider.kannel")
@ConstructorBinding
data class KannelSmsProviderProperties(
        val sendBaseUrl: String,
        val username: String,
        val password: String
)