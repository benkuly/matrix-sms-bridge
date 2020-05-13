package net.folivo.matrix.bridge.sms.mapping

import net.folivo.matrix.bot.appservice.room.AppserviceRoom
import net.folivo.matrix.bot.appservice.user.AppserviceUser
import org.neo4j.springframework.data.core.schema.*
import org.neo4j.springframework.data.core.schema.Relationship.Direction.OUTGOING
import org.springframework.data.annotation.Version

@Node("SmsRoom")
data class SmsRoom(
        @Property("mappingToken")
        val mappingToken: Int,

        @Relationship(type = "BRIDGED_TO", direction = OUTGOING)
        val bridgedRoom: AppserviceRoom,
        @Relationship(type = "OWNED_BY", direction = OUTGOING)
        val user: AppserviceUser
) {
    @Id
    @GeneratedValue
    var id: Long? = null
        private set

    @Version
    var version: Long = 0
}