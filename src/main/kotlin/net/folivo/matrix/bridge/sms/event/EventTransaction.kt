package net.folivo.matrix.bridge.sms.event

import org.neo4j.springframework.data.core.schema.GeneratedValue
import org.neo4j.springframework.data.core.schema.Id
import org.neo4j.springframework.data.core.schema.Node
import org.neo4j.springframework.data.core.schema.Property
import org.springframework.data.annotation.Version

@Node("EventTransaction")
data class EventTransaction(
        @Property("tnxId")
        var tnxId: String,
        @Property("eventIdElseType")
        var eventIdElseType: String
) {
    @Id
    @GeneratedValue
    var id: Long? = null
        private set

    @Version
    var version: Long? = null
        private set
}