package net.folivo.matrix.bridge.sms.user

import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.neo4j.driver.springframework.boot.test.autoconfigure.Neo4jTestHarnessAutoConfiguration
import org.neo4j.springframework.boot.test.autoconfigure.data.ReactiveDataNeo4jTest
import org.neo4j.springframework.data.core.ReactiveNeo4jTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.Neo4jContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.test.StepVerifier


@ReactiveDataNeo4jTest(excludeAutoConfiguration = [Neo4jTestHarnessAutoConfiguration::class])
@Testcontainers
class AppserviceUserRepositoryIT {

    companion object {
        @Container
        private val neo4jContainer: Neo4jContainer<*> = Neo4jContainer<Nothing>("neo4j:4.0")

        @DynamicPropertySource
        @JvmStatic
        fun neo4jProperties(registry: DynamicPropertyRegistry) {
            registry.add("org.neo4j.driver.uri", neo4jContainer::getBoltUrl)
            registry.add("org.neo4j.driver.authentication.username") { "neo4j" }
            registry.add("org.neo4j.driver.authentication.password", neo4jContainer::getAdminPassword)
        }
    }


    @Autowired
    lateinit var cut: AppserviceUserRepository

    @Autowired
    lateinit var template: ReactiveNeo4jTemplate


    lateinit var room1: AppserviceRoom
    lateinit var room2: AppserviceRoom
    lateinit var user1: AppserviceUser
    lateinit var user2: AppserviceUser
    lateinit var user3: AppserviceUser


    @BeforeEach
    fun beforeEach() {
        template.deleteAll(AppserviceRoom::class.java).block()
        template.deleteAll(AppserviceUser::class.java).block()

        user1 = template.save(AppserviceUser("someUserId1", true)).block() ?: throw RuntimeException()
        user2 = template.save(AppserviceUser("someUserId2", true)).block() ?: throw RuntimeException()
        user3 = template.save(AppserviceUser("someUserId3", true)).block() ?: throw RuntimeException()

        room1 = template.save(
                AppserviceRoom(
                        "someRoomId1",
                        mutableMapOf(
                                user1 to MemberOfProperties(1),
                                user2 to MemberOfProperties(1)
                        )
                )
        ).block() ?: throw RuntimeException()
        room2 = template.save(AppserviceRoom("someRoomId2", mutableMapOf(user1 to MemberOfProperties(24)))).block()
                ?: throw RuntimeException()
    }

    @Test
    fun `should findLastMappingTokenByUserId`() {
        StepVerifier
                .create(cut.findLastMappingTokenByUserId("someUserId1"))
                .assertNext { assertThat(it).isEqualTo(24) }
                .verifyComplete()
    }

    @Test
    fun `should not findLastMappingTokenByUserId when user not in room`() {
        StepVerifier
                .create(cut.findLastMappingTokenByUserId("someUserId3"))
                .verifyComplete()
    }

    @Test
    fun `should not findLastMappingTokenByUserId when user does not exist`() {
        StepVerifier
                .create(cut.findLastMappingTokenByUserId("notExistingUserId"))
                .verifyComplete()
    }
}