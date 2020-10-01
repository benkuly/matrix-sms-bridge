package net.folivo.matrix.bridge.sms.room

import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import org.springframework.data.neo4j.core.schema.Id
import org.springframework.data.neo4j.core.schema.Node
import org.springframework.data.neo4j.core.schema.Relationship
import org.springframework.data.neo4j.core.schema.Relationship.Direction.OUTGOING

@Node("AppserviceRoom")
data class AppserviceRoom(
        @Id
        val roomId: String,

        @Relationship(type = "MEMBER_OF", direction = OUTGOING)
        val members: List<MemberOfProperties> = listOf()
)