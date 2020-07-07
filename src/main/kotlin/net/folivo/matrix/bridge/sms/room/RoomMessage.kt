package net.folivo.matrix.bridge.sms.room

import org.neo4j.springframework.data.core.schema.GeneratedValue
import org.neo4j.springframework.data.core.schema.Id
import org.neo4j.springframework.data.core.schema.Node
import org.neo4j.springframework.data.core.schema.Property
import org.springframework.data.annotation.Version
import java.time.LocalDateTime

@Node("EventMessage")
data class RoomMessage(
        @Property("roomId")
        var roomId: String,
        @Property("body")
        var body: String,
        @Property("sendAfter")
        var sendAfter: LocalDateTime = LocalDateTime.now(),
        @Property("requiredReceiverIds")
        var requiredReceiverIds: Set<String> = emptySet()
) {
    @Id
    @GeneratedValue
    var id: Long? = null
        private set

    @Version
    var version: Long? = null
        private set
}