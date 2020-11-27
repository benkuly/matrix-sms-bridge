package net.folivo.matrix.bridge.sms.message

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.reactive.awaitFirst
import net.folivo.matrix.bot.config.MatrixBotDatabaseAutoconfiguration
import net.folivo.matrix.bot.room.MatrixRoom
import net.folivo.matrix.bridge.sms.SmsBridgeDatabaseConfiguration
import net.folivo.matrix.core.model.MatrixId.RoomId
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.core.delete

@DataR2dbcTest
@ImportAutoConfiguration(value = [MatrixBotDatabaseAutoconfiguration::class, SmsBridgeDatabaseConfiguration::class])
class MatrixMessageRepositoryTest(
        cut: MatrixMessageRepository,
        db: R2dbcEntityTemplate
) : DescribeSpec(testBody(cut, db))

private fun testBody(cut: MatrixMessageRepository, db: R2dbcEntityTemplate): DescribeSpec.() -> Unit {
    return {
        val room1 = RoomId("room1", "server")
        val room2 = RoomId("room2", "server")

        val message1 = MatrixMessage(room1, "some body 1")
        val message2 = MatrixMessage(room1, "some body 2")
        val message3 = MatrixMessage(room2, "some body 3")

        beforeSpec {
            db.insert(MatrixRoom(room1)).awaitFirst()
            db.insert(MatrixRoom(room2)).awaitFirst()
            db.insert(message1).awaitFirst()
            db.insert(message2).awaitFirst()
            db.insert(message3).awaitFirst()
        }

        describe(MatrixMessageRepository::deleteByRoomId.name) {
            it("should delete all matching rooms") {
                cut.count().shouldBe(3)
                cut.deleteByRoomId(room1)
                cut.count().shouldBe(1)
            }
        }

        afterSpec {
            db.delete<MatrixMessage>().all().awaitFirst()
            db.delete<MatrixRoom>().all().awaitFirst()
        }
    }
}