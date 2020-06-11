package net.folivo.matrix.bridge.sms

import org.neo4j.springframework.data.repository.config.EnableReactiveNeo4jRepositories
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@EnableReactiveNeo4jRepositories
@EnableTransactionManagement
@EnableConfigurationProperties(SmsBridgeProperties::class)
class SmsBridgeApplication

fun main(args: Array<String>) {
    runApplication<SmsBridgeApplication>(*args)
}