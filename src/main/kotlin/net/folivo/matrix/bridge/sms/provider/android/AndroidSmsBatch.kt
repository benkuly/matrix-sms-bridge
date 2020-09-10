package net.folivo.matrix.bridge.sms.provider.android

import org.neo4j.springframework.data.core.schema.Id
import org.neo4j.springframework.data.core.schema.Node
import org.neo4j.springframework.data.core.schema.Property
import org.springframework.data.annotation.Version

@Node("AndroidSmsBatch")
data class AndroidSmsBatch(
        @Id
        val id: Long,
        @Property("nextBatch")
        var nextBatch: String,
) {
    @Version
    var version: Long? = null
        private set
}