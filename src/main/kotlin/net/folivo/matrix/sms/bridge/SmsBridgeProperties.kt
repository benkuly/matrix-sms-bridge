package net.folivo.matrix.sms.bridge

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("matrix.sms.bridge")
@ConstructorBinding
data class SmsBridgeProperties(
        val defaultRoom: String? = null
)