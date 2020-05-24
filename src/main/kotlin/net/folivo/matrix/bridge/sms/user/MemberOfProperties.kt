package net.folivo.matrix.bridge.sms.user

import org.neo4j.springframework.data.core.schema.Property
import org.neo4j.springframework.data.core.schema.RelationshipProperties

@RelationshipProperties
data class MemberOfProperties(
        @Property("mappingToken")
        val mappingToken: Int
)