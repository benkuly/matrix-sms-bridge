package net.folivo.matrix.bridge.sms.provider

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@Configuration
class SmsProviderConfiguration {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    @ConditionalOnMissingBean
    fun noOpSmsProvider(): SmsProvider {
        return NoOpSmsProvider()
    }
}