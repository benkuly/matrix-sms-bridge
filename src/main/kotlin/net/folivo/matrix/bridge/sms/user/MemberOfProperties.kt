package net.folivo.matrix.bridge.sms.user

import org.springframework.data.neo4j.core.schema.Property
import org.springframework.data.neo4j.core.schema.RelationshipProperties
import org.springframework.data.neo4j.core.schema.TargetNode


@RelationshipProperties
data class MemberOfProperties(
        @TargetNode
        val member: AppserviceUser,

        @Property("mappingToken")
        val mappingToken: Int
)