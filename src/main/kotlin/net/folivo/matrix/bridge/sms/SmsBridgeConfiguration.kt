package net.folivo.matrix.bridge.sms

import net.folivo.matrix.bridge.sms.provider.NoOpSmsProvider
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

@Configuration
class SmsBridgeConfiguration {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    @ConditionalOnMissingBean
    fun noOpSmsProvider(): SmsProvider {
        return NoOpSmsProvider()
    }

}