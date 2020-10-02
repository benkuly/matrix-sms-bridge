package net.folivo.matrix.bridge.sms

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveDataAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import org.springframework.data.neo4j.repository.config.EnableReactiveNeo4jRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@EnableReactiveNeo4jRepositories
@EnableTransactionManagement //FIXME
@Import(Neo4jReactiveDataAutoConfiguration::class)
@EnableConfigurationProperties(SmsBridgeProperties::class)
class SmsBridgeApplication {

//    //TODO should usually not be needed (bug?)
//    @Bean(ReactiveNeo4jRepositoryConfigurationExtension.DEFAULT_TRANSACTION_MANAGER_BEAN_NAME)
//    fun reactiveTransactionManager(
//            driver: Driver?,
//            databaseNameProvider: ReactiveDatabaseSelectionProvider?
//    ): ReactiveTransactionManager? {
//        return ReactiveNeo4jTransactionManager(driver!!, databaseNameProvider!!)
//    }
}

fun main(args: Array<String>) {
    runApplication<SmsBridgeApplication>(*args)
}