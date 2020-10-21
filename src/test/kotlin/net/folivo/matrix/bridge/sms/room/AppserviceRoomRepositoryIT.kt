package net.folivo.matrix.bridge.sms.room

import kotlinx.coroutines.runBlocking
import net.folivo.matrix.bridge.sms.mapping.MatrixSmsMapping
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.neo4j.DataNeo4jTest
import org.springframework.data.neo4j.core.ReactiveNeo4jTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.Neo4jContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.test.StepVerifier


@DataNeo4jTest
@Testcontainers
class AppserviceRoomRepositoryIT {

    companion object {
        @Container
        private val neo4jContainer: Neo4jContainer<*> = Neo4jContainer<Nothing>("neo4j:4.0")

        @DynamicPropertySource
        @JvmStatic
        fun neo4jProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.neo4j.uri", neo4jContainer::getBoltUrl)
            registry.add("spring.neo4j.authentication.username") { "neo4j" }
            registry.add("spring.neo4j.authentication.password", neo4jContainer::getAdminPassword)
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
    lateinit var user5: AppserviceUser


    @BeforeEach
    fun beforeEach() {
        template.deleteAll(AppserviceRoom::class.java).block()
        template.deleteAll(AppserviceUser::class.java).block()

        user1 = template.save(AppserviceUser("someUserId1", true)).block() ?: throw RuntimeException()
        user2 = template.save(AppserviceUser("someUserId2", true)).block() ?: throw RuntimeException()
        user3 = template.save(AppserviceUser("someUserId3", true)).block() ?: throw RuntimeException()
        user4 = template.save(AppserviceUser("someUserId4", true)).block() ?: throw RuntimeException()
        user5 = template.save(AppserviceUser("someUserId4", true)).block() ?: throw RuntimeException()


        room1 = template.save(
                AppserviceRoom(
                        "someRoomId1",
                        memberships = listOf(
                                MatrixSmsMapping(user1, 1),
                                MatrixSmsMapping(user2, 1),
                                MatrixSmsMapping(user3, 1)
                        )
                )

        ).block() ?: throw RuntimeException()
        room2 = template.save(
                AppserviceRoom(
                        "someRoomId2",
                        memberships = listOf(
                                MatrixSmsMapping(user1, 24),
                                MatrixSmsMapping(user2, 2),
                                MatrixSmsMapping(user4, 2)
                        )
                )
        ).block() ?: throw RuntimeException()
    }

    @Test
    fun `room should contain users`() {
        StepVerifier
                .create(cut.findById("someRoomId1"))
                .assertNext { room ->
                    assertThat(room.roomId).isEqualTo("someRoomId1")
                    assertThat(room.memberships.map { it.member.userId }).containsAll(
                            listOf(
                                    "someUserId1",
                                    "someUserId2"
                            )
                    )
                }.verifyComplete()
    }

    @Test
    fun `should findByMembersUserIdContaining one`() {
        StepVerifier
                .create(cut.findByMembersUserIdContaining(setOf("someUserId1", "someUserId3")))
                .assertNext { assertThat(it.roomId).isEqualTo("someRoomId1") }.verifyComplete()
    }

    @Test
    fun `should findByMembersUserIdContaining two`() {
        StepVerifier
                .create(cut.findByMembersUserIdContaining(setOf("someUserId1", "someUserId2")))
                .assertNext { assertThat(it.roomId).isEqualTo("someRoomId1") }
                .assertNext { assertThat(it.roomId).isEqualTo("someRoomId2") }
                .verifyComplete()
    }

    @Test
    fun `should not findByMembersUserIdContaining`() {
        StepVerifier
                .create(cut.findByMembersUserIdContaining(setOf("someUserId3", "someUserId4")))
                .verifyComplete()
    }

    @Test
    fun `should not findByMembersUserIdContaining with foreign userid`() {
        StepVerifier
                .create(cut.findByMembersUserIdContaining(setOf("someUserId2", "someUserId24")))
                .verifyComplete()
    }

    @Test
    fun `should findAllByUserId`() {
        val result1 = runBlocking { cut.findAllByUserId("someUserId1").asFlow().toList() }
        result1.map { it.roomId }.shouldContainExactlyInAnyOrder("someRoomId1", "someRoomId2")

        val result2 = runBlocking { cut.findAllByUserId("someUserId4").asFlow().toList() }
        result2.map { it.roomId }.shouldContainExactlyInAnyOrder("someRoomId2")
    }

    @Test
    fun `should not findAllByUserId`() {
        StepVerifier
                .create(cut.findAllByUserId("someUnknownUserId"))
                .verifyComplete()
        StepVerifier
                .create(cut.findAllByUserId("someUserId5"))
                .verifyComplete()
    }

    @Test
    fun `should findByUserIdAndMappingToken`() {
        StepVerifier
                .create(cut.findByUserIdAndMappingToken("someUserId1", 24))
                .assertNext { it.roomId.shouldBe("someRoomId2") }
                .verifyComplete()
        StepVerifier
                .create(cut.findByUserIdAndMappingToken("someUserId4", 2))
                .assertNext { it.roomId.shouldBe("someRoomId2") }
                .verifyComplete()
    }

    @Test
    fun `should not findByUserIdAndMappingToken`() {
        StepVerifier
                .create(cut.findByUserIdAndMappingToken("someUserId4", 33))
                .verifyComplete()
        StepVerifier
                .create(cut.findByUserIdAndMappingToken("someUserId5", 1))
                .verifyComplete()
        StepVerifier
                .create(cut.findByUserIdAndMappingToken("someUnknownUserId", 1))
                .verifyComplete()
    }

    @Test
    fun `should save room leave`() {
        val removeIt = MatrixSmsMapping(user1, 1)
        val room = template.save(
                AppserviceRoom(
                        "someRoomId",
                        memberships = listOf(
                                removeIt,
                                MatrixSmsMapping(user2, 1)
                        )
                )
        ).block() ?: throw RuntimeException()

        cut.save(room.copy(members = room.members.minus(removeIt))).block()

        StepVerifier
                .create(cut.findById("someRoomId"))
                .assertNext { it.memberships.size.shouldBe(1) }
                .verifyComplete()
    }
}