package net.folivo.matrix.bridge.sms.room

import org.neo4j.springframework.data.core.schema.*
import org.neo4j.springframework.data.core.schema.Relationship.Direction.INCOMING
import org.springframework.data.annotation.Version
import java.time.Instant

@Node("EventMessage")
data class RoomMessage(
        @Relationship(type = "MEMBER_OF", direction = INCOMING)
        val room: AppserviceRoom,
        @Property("body")
        var body: String,
        @Property("sendAfter")
        var sendAfter: Instant = Instant.now(),
        @Property("requiredReceiverIds")
        var requiredReceiverIds: Set<String> = emptySet(),
        @Property("isNotice")
        val isNotice: Boolean = false
) {
    @Id
    @GeneratedValue
    var id: Long? = null
        private set

    @Version
    var version: Long? = null
        private set
}