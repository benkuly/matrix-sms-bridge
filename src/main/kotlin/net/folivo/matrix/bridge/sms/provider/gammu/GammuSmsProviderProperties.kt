package net.folivo.matrix.bridge.sms.provider.gammu

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConfigurationProperties("matrix.bridge.sms.provider.gammu")
@ConstructorBinding
data class GammuSmsProviderProperties(
    val enabled: Boolean = false,
    val inboxPath: String = "/data/spool/inbox",
    val inboxProcessedPath: String = "/data/spool/inbox_processed",
    val configFile: String = "/etc/gammu/gammu-smsdrc-modem1"
)
