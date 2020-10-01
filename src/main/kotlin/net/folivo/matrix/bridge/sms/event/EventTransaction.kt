package net.folivo.matrix.bridge.sms.event

import org.springframework.data.annotation.Version
import org.springframework.data.neo4j.core.schema.GeneratedValue
import org.springframework.data.neo4j.core.schema.Id
import org.springframework.data.neo4j.core.schema.Node
import org.springframework.data.neo4j.core.schema.Property

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