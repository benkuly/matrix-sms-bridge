package net.folivo.matrix.bridge.sms.room

import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
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
class AppserviceRoomRepositoryIT {

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
    lateinit var cut: AppserviceRoomRepository

    @Autowired
    lateinit var template: ReactiveNeo4jTemplate


    lateinit var room1: AppserviceRoom
    lateinit var room2: AppserviceRoom
    lateinit var user1: AppserviceUser
    lateinit var user2: AppserviceUser
    lateinit var user3: AppserviceUser
    lateinit var user4: AppserviceUser


    @BeforeEach
    fun beforeEach() {
        template.deleteAll(AppserviceRoom::class.java).block()
        template.deleteAll(AppserviceUser::class.java).block()

        room1 = template.save(AppserviceRoom("someRoomId1")).block() ?: throw RuntimeException()
        room2 = template.save(AppserviceRoom("someRoomId2")).block() ?: throw RuntimeException()

        user1 = template.save(
                AppserviceUser(
                        "someUserId1",
                        mutableMapOf(
                                room1 to MemberOfProperties(1),
                                room2 to MemberOfProperties(24)
                        )
                )
        ).block() ?: throw RuntimeException()
        user2 = template.save(
                AppserviceUser(
                        "someUserId2",
                        mutableMapOf(
                                room1 to MemberOfProperties(1)
                        )
                )
        ).block() ?: throw RuntimeException()
        user3 = template.save(
                AppserviceUser(
                        "someUserId3",
                        mutableMapOf(
                                room1 to MemberOfProperties(1),
                                room2 to MemberOfProperties(2)
                        )
                )
        ).block() ?: throw RuntimeException()
        user4 = template.save(
                AppserviceUser(
                        "someUserId4",
                        mutableMapOf(
                                room2 to MemberOfProperties(2)
                        )
                )
        ).block() ?: throw RuntimeException()
    }

    @Test
    fun `should findByMembersUserIdContaining one`() {
        StepVerifier
                .create(cut.findByMembersUserIdContaining(setOf("someUserId1", "someUserId2")))
                .assertNext { assertThat(it.roomId).isEqualTo("someRoomId1") }
                .verifyComplete()
    }

    @Test
    fun `should findByMembersUserIdContaining two`() {
        StepVerifier
                .create(cut.findByMembersUserIdContaining(setOf("someUserId1", "someUserId3")))
                .assertNext { assertThat(it.roomId).isEqualTo("someRoomId1") }
                .assertNext { assertThat(it.roomId).isEqualTo("someRoomId2") }
                .verifyComplete()
    }

    @Test
    fun `should not findByMembersUserIdContaining`() {
        StepVerifier
                .create(cut.findByMembersUserIdContaining(setOf("someUserId2", "someUserId4")))
                .verifyComplete()
    }

    @Test
    fun `should not findByMembersUserIdContaining with foreign userid`() {
        StepVerifier
                .create(cut.findByMembersUserIdContaining(setOf("someUserId2", "someUserId24")))
                .verifyComplete()
    }

}