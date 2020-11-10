package net.folivo.matrix.bridge.sms.mapping

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.awaitFirst
import net.folivo.matrix.bot.config.MatrixBotDatabaseAutoconfiguration
import net.folivo.matrix.bot.membership.MatrixMembership
import net.folivo.matrix.bot.room.MatrixRoom
import net.folivo.matrix.bot.user.MatrixUser
import net.folivo.matrix.bridge.sms.SmsBridgeDatabaseConfiguration
import net.folivo.matrix.core.model.MatrixId.RoomId
import net.folivo.matrix.core.model.MatrixId.UserId
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.data.r2dbc.core.DatabaseClient
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.core.delete

@DataR2dbcTest
@ImportAutoConfiguration(value = [MatrixBotDatabaseAutoconfiguration::class, SmsBridgeDatabaseConfiguration::class])
class MatrixSmsMappingRepositoryTest(
        cut: MatrixSmsMappingRepository,
        dbClient: DatabaseClient
) : DescribeSpec(testBody(cut, dbClient))

private fun testBody(cut: MatrixSmsMappingRepository, dbClient: DatabaseClient): DescribeSpec.() -> Unit {
    return {
        val entityTemplate = R2dbcEntityTemplate(dbClient)

        val user1 = UserId("user1", "server")
        val user2 = UserId("user2", "server")
        val user3 = UserId("user3", "server")
        val room1 = RoomId("room1", "server")
        val room2 = RoomId("room2", "server")
        val room3 = RoomId("room3", "server")

        var map1: MatrixSmsMapping? = null
        var map2: MatrixSmsMapping? = null
        var map3: MatrixSmsMapping? = null
        var map4: MatrixSmsMapping? = null
        var map5: MatrixSmsMapping? = null

        beforeSpec {
            entityTemplate.insert(MatrixUser(user1)).awaitFirst()
            entityTemplate.insert(MatrixUser(user2)).awaitFirst()
            entityTemplate.insert(MatrixUser(user3)).awaitFirst()
            entityTemplate.insert(MatrixRoom(room1)).awaitFirst()
            entityTemplate.insert(MatrixRoom(room2)).awaitFirst()
            entityTemplate.insert(MatrixRoom(room3)).awaitFirst()
            val mem1 = entityTemplate.insert(MatrixMembership(user1, room1)).awaitFirst().id
            val mem2 = entityTemplate.insert(MatrixMembership(user2, room1)).awaitFirst().id
            val mem3 = entityTemplate.insert(MatrixMembership(user1, room2)).awaitFirst().id
            val mem4 = entityTemplate.insert(MatrixMembership(user1, room3)).awaitFirst().id
            val mem5 = entityTemplate.insert(MatrixMembership(user2, room3)).awaitFirst().id
            map1 = entityTemplate.insert(MatrixSmsMapping(mem1, 5)).awaitFirst()
            map2 = entityTemplate.insert(MatrixSmsMapping(mem2, 4)).awaitFirst()
            map3 = entityTemplate.insert(MatrixSmsMapping(mem3, 9)).awaitFirst()
            map4 = entityTemplate.insert(MatrixSmsMapping(mem4, 1)).awaitFirst()
            map5 = entityTemplate.insert(MatrixSmsMapping(mem5, 1)).awaitFirst()
        }

        describe(MatrixSmsMappingRepository::findByUserIdSortByMappingTokenDesc.name) {
            it("should find and sort mapping tokens") {
                cut.findByUserIdSortByMappingTokenDesc(user1).toList()
                        .shouldContainInOrder(map3, map1, map4)
            }
            it("should not find and sort mapping tokens") {
                cut.findByUserIdSortByMappingTokenDesc(user3).toList()
                        .shouldBeEmpty()
            }
        }

        describe(MatrixSmsMappingRepository::findByUserIdAndMappingToken.name) {
            it("should find by user id and mapping token") {
                cut.findByUserIdAndMappingToken(user2, 1)
                        .shouldBe(map5)
            }
            it("should not find by user id and mapping token") {
                cut.findByUserIdAndMappingToken(user2, 5)
                        .shouldBeNull()
                cut.findByUserIdAndMappingToken(user3, 1)
                        .shouldBeNull()
            }
        }


        afterSpec {
            entityTemplate.delete<MatrixSmsMapping>().all().awaitFirst()
            entityTemplate.delete<MatrixMembership>().all().awaitFirst()
            entityTemplate.delete<MatrixRoom>().all().awaitFirst()
            entityTemplate.delete<MatrixUser>().all().awaitFirst()
        }
    }
}