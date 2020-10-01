package net.folivo.matrix.bridge.sms.provider.android

import org.springframework.data.annotation.Version
import org.springframework.data.neo4j.core.schema.Id
import org.springframework.data.neo4j.core.schema.Node
import org.springframework.data.neo4j.core.schema.Property

@Node("AndroidSmsBatch")
data class AndroidSmsBatch(
        @Id
        val id: Long,
        @Property("nextBatch")
        var nextBatch: String,
        @Version
        var version: Long? = null
)