package net.folivo.matrix.bridge.sms.mapping

import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import org.neo4j.springframework.data.core.schema.*
import org.neo4j.springframework.data.core.schema.Relationship.Direction.OUTGOING
import org.springframework.data.annotation.Version

@Node("SmsRoom")
data class SmsRoom(
        @Property("mappingToken")
        val mappingToken: Int,
        @Relationship(type = "OWNED_BY", direction = OUTGOING)
        val user: AppserviceUser,
        @Relationship(type = "BRIDGED_TO", direction = OUTGOING)
        val bridgedRoom: AppserviceRoom
) {
    @Id
    @GeneratedValue
    var id: Long? = null
        private set

    @Version
    var version: Long = 0
        private set
}