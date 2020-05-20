package net.folivo.matrix.bridge.sms.provider.gammu

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("matrix.bridge.sms.provider.gammu")
@ConstructorBinding
data class GammuSmsProviderProperties(
        val enabled: Boolean = false,
        val inboxPath: String = "/var/spool/gammu/inbox",
        val inboxProcessedPath: String = "/var/spool/gammu/inbox_processed"
)