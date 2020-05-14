package net.folivo.matrix.bridge.sms.mapping

import net.folivo.matrix.bot.appservice.room.AppserviceRoom
import net.folivo.matrix.bot.appservice.user.AppserviceUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.neo4j.driver.springframework.boot.test.autoconfigure.Neo4jTestHarnessAutoConfiguration
import org.neo4j.springframework.boot.test.autoconfigure.data.ReactiveDataNeo4jTest
import org.neo4j.springframework.data.core.ReactiveNeo4jClient
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
class SmsRoomRepositoryIT {

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
    lateinit var cut: SmsRoomRepository

    @Autowired
    lateinit var reactiveNeo4jClient: ReactiveNeo4jClient

    @Autowired
    lateinit var template: ReactiveNeo4jTemplate


    lateinit var room1: AppserviceRoom
    lateinit var room2: AppserviceRoom
    lateinit var user1: AppserviceUser
    lateinit var user2: AppserviceUser


    @BeforeEach
    fun beforeEach() {
        template.deleteAll(AppserviceRoom::class.java).block()
        template.deleteAll(AppserviceUser::class.java).block()
        template.deleteAll(SmsRoom::class.java).block()

        room1 = template.save(AppserviceRoom("someRoomId1")).block() ?: throw RuntimeException()
        room2 = template.save(AppserviceRoom("someRoomId2")).block() ?: throw RuntimeException()

        user1 = template.save(AppserviceUser("someUserId1", mutableSetOf(room1, room2))).block()
                ?: throw RuntimeException()
        user2 = template.save(AppserviceUser("someUserId2", mutableSetOf(room1))).block() ?: throw RuntimeException()
    }

    @Test
    fun `should findLastMappingTokenByUserId`() {
        cut.save(SmsRoom(1, user1, room1)).block()
        cut.save(SmsRoom(24, user1, room2)).block()

        StepVerifier
                .create(cut.findLastMappingTokenByUserId("someUserId1"))
                .assertNext { assertThat(it).isEqualTo(24) }
                .verifyComplete()
    }

    @Test
    fun `should not findLastMappingTokenByUserId`() {
        cut.save(SmsRoom(1, user1, room1)).block()

        StepVerifier
                .create(cut.findLastMappingTokenByUserId("notExistingUserId"))
                .verifyComplete()
    }

    @Test
    fun `should findByBridgedRoomRoomIdAndUserUserId`() {
        val expectedResult = cut.save(SmsRoom(4, user1, room1)).block() ?: throw RuntimeException()
        cut.save(SmsRoom(24, user2, room2)).block()
        StepVerifier
                .create(cut.findByBridgedRoomRoomIdAndUserUserId("someRoomId1", "someUserId1"))
                .assertNext {
                    assertThat(it.mappingToken).isEqualTo(expectedResult.mappingToken)
                    assertThat(it.user.userId).isEqualTo(expectedResult.user.userId)
                    assertThat(it.bridgedRoom.roomId).isEqualTo(expectedResult.bridgedRoom.roomId)
                }
                .verifyComplete()
    }

    @Test
    fun `should not findByBridgedRoomRoomIdAndUserUserId`() {
        cut.save(SmsRoom(4, user1, room1)).block()
        StepVerifier
                .create(cut.findByBridgedRoomRoomIdAndUserUserId("someRoomId2", "someUserId2"))
                .verifyComplete()
    }

    @Test
    fun `should findByMappingTokenAndUserUserId`() {
        val expectedResult = cut.save(SmsRoom(4, user1, room1)).block() ?: throw RuntimeException()
        cut.save(SmsRoom(24, user2, room2)).block()
        StepVerifier
                .create(cut.findByMappingTokenAndUserUserId(4, "someUserId1"))
                .assertNext {
                    assertThat(it.mappingToken).isEqualTo(expectedResult.mappingToken)
                    assertThat(it.user.userId).isEqualTo(expectedResult.user.userId)
                    assertThat(it.bridgedRoom.roomId).isEqualTo(expectedResult.bridgedRoom.roomId)
                }
                .verifyComplete()
    }

    @Test
    fun `should not findByMappingTokenAndUserUserId`() {
        cut.save(SmsRoom(4, user1, room1)).block()
        StepVerifier
                .create(cut.findByMappingTokenAndUserUserId(24, "someUserId1"))
                .verifyComplete()
    }
}