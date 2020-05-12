package net.folivo.matrix.bot.appservice.room

import org.neo4j.springframework.data.core.schema.*
import org.neo4j.springframework.data.core.schema.Relationship.Direction.OUTGOING
import org.springframework.data.annotation.Version

@Node("SmsRoom")
data class SmsRoom(
        @Property("mappingToken")
        val mappingToken: String,

        @Relationship(type = "BRIDGED_TO", direction = OUTGOING)
        val bridgedRoom: AppserviceRoom
) {
    @Id
    @GeneratedValue
    var id: Long? = null
        private set

    @Version
    var version: Long = 0
}