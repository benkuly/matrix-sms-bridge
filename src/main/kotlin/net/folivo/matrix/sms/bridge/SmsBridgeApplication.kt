package net.folivo.matrix.sms.bridge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties

@SpringBootApplication
@EnableConfigurationProperties(SmsBridgeProperties::class)
class SmsBridgeApplication {
}