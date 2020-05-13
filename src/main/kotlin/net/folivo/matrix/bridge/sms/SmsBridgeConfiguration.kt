package net.folivo.matrix.bridge.sms

import net.folivo.matrix.bridge.sms.SmsBridgeProperties.SmsProviderName.KANNEL
import net.folivo.matrix.bridge.sms.provider.KannelSmsProvider
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SmsBridgeConfiguration(private val smsBridgeProperties: SmsBridgeProperties) {

    @Bean
    fun smsProvider(): SmsProvider {
        return when (smsBridgeProperties.provider) {
            KANNEL -> KannelSmsProvider()
        }
    }

}