package net.folivo.matrix.bridge.sms.sync

import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.restclient.MatrixClient
import net.folivo.matrix.restclient.api.rooms.GetJoinedMembersResponse
import net.folivo.matrix.restclient.api.rooms.GetJoinedMembersResponse.RoomMember
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.neo4j.driver.springframework.boot.test.autoconfigure.Neo4jTestHarnessAutoConfiguration
import org.neo4j.springframework.data.core.ReactiveNeo4jTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.Neo4jContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers


@SpringBootTest
@EnableAutoConfiguration(exclude = [Neo4jTestHarnessAutoConfiguration::class])
@Testcontainers
@ActiveProfiles(profiles = ["initialsync"])
class InitialSyncServiceIT {

    companion object {
        @Container
        private val neo4jContainer: Neo4jContainer<*> = Neo4jContainer<Nothing>("neo4j:4.1")

        @DynamicPropertySource
        @JvmStatic
        fun neo4jProperties(registry: DynamicPropertyRegistry) {
            registry.add("org.neo4j.driver.uri", neo4jContainer::getBoltUrl)
            registry.add("org.neo4j.driver.authentication.username") { "neo4j" }
            registry.add("org.neo4j.driver.authentication.password", neo4jContainer::getAdminPassword)
        }
    }


    @Autowired
    lateinit var cut: InitialSyncService

    @Autowired
    lateinit var template: ReactiveNeo4jTemplate

    @MockkBean(relaxed = true)
    lateinit var api: MatrixClient

    lateinit var room1: AppserviceRoom
    lateinit var room2: AppserviceRoom
    lateinit var user1: AppserviceUser
    lateinit var user2: AppserviceUser


    @BeforeEach
    fun beforeEach() {
        template.deleteAll(AppserviceRoom::class.java).block()
        template.deleteAll(AppserviceUser::class.java).block()

        //before initialsync
        // room1 -> user1, user2
        // room2 -> user1
        user1 = template.save(AppserviceUser("someUserId1", true)).block() ?: throw RuntimeException()
        user2 = template.save(AppserviceUser("someUserId2", true)).block() ?: throw RuntimeException()

        room1 = template.save(
                AppserviceRoom(
                        "someRoomId1",
                        members = mutableMapOf(
                                user1 to MemberOfProperties(1),
                                user2 to MemberOfProperties(1)
                        )
                )
        )
                        .block() ?: throw RuntimeException()
        room2 = template.save(AppserviceRoom("someRoomId2", members = mutableMapOf(user1 to MemberOfProperties(24))))
                        .block() ?: throw RuntimeException()

        //after initialsync
        // room1 -> user1, user3
        coEvery { api.roomsApi.getJoinedRooms() }
                .returns(flowOf("someRoomId1"))
        coEvery { api.roomsApi.getJoinedMembers("someRoomId1") }
                .returns(GetJoinedMembersResponse(mapOf("someUserId1" to RoomMember(), "someUserId3" to RoomMember())))
    }

    @Test
    fun `should have synced initially`() {
        cut.onApplicationEvent(mockk()) // TODO is there a way to prevent call on startup?
        val users = template.findAll(AppserviceUser::class.java).collectList().block()
        val rooms = template.findAll(AppserviceRoom::class.java).collectList().block()

        assertThat(rooms).size().isEqualTo(1)
        assertThat(rooms?.get(0)?.roomId).isEqualTo("someRoomId1")
        assertThat(rooms?.get(0)?.members?.size).isEqualTo(2)

        assertThat(users).size().isEqualTo(2)

        assertThat(users?.get(0)).isEqualTo(
                AppserviceUser(
                        "someUserId1", false
                )
        )
        assertThat(users?.get(1)).isEqualTo(
                AppserviceUser(
                        "someUserId3", false
                )
        )

    }
}