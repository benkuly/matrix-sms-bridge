package net.folivo.matrix.sms.bridge

import org.neo4j.springframework.data.repository.config.EnableReactiveNeo4jRepositories
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties

@SpringBootApplication
@EnableReactiveNeo4jRepositories
@EnableConfigurationProperties(SmsBridgeProperties::class)
class SmsBridgeApplication {
}