package net.folivo.matrix.bridge.sms.room

import org.springframework.data.annotation.Version
import org.springframework.data.neo4j.core.schema.*
import org.springframework.data.neo4j.core.schema.Relationship.Direction.INCOMING
import java.time.Instant

@Node("RoomMessage")
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