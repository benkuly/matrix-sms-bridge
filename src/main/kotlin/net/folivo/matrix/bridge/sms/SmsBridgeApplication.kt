package net.folivo.matrix.bridge.sms

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories

@SpringBootApplication
@EnableR2dbcRepositories
@EnableConfigurationProperties(SmsBridgeProperties::class)
class SmsBridgeApplication

fun main(args: Array<String>) {
    runApplication<SmsBridgeApplication>(*args)
}