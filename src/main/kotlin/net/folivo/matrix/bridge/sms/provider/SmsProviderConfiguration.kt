package net.folivo.matrix.bridge.sms.provider

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SmsProviderConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun noOpSmsProvider(): SmsProvider {
        return NoOpSmsProvider()
    }
}