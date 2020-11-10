package net.folivo.matrix.bridge.sms

import liquibase.integration.spring.SpringLiquibase
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import javax.sql.DataSource

@Configuration
@EnableAutoConfiguration(exclude = [JdbcRepositoriesAutoConfiguration::class])
class SmsBridgeDatabaseConfiguration {

    @Bean
    @DependsOn("liquibase")
    fun smsLiquibase(@Qualifier("liquibaseDatasource") liquibaseDatasource: DataSource): SpringLiquibase {
        return SpringLiquibase().apply {
            changeLog = "classpath:db/changelog/net.folivo.matrix.bridge.sms.changelog-master.yml"
            dataSource = liquibaseDatasource
        }
    }

}