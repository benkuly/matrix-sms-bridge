package net.folivo.matrix.bridge.sms

import org.neo4j.springframework.data.repository.config.EnableReactiveNeo4jRepositories
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableReactiveNeo4jRepositories
@EnableConfigurationProperties(SmsBridgeProperties::class)
class SmsBridgeApplication

fun main(args: Array<String>) {
    runApplication<SmsBridgeApplication>(*args)
}