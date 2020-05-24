package net.folivo.matrix.bridge.sms.user

import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import org.neo4j.springframework.data.core.schema.Id
import org.neo4j.springframework.data.core.schema.Node
import org.neo4j.springframework.data.core.schema.Property
import org.neo4j.springframework.data.core.schema.Relationship
import org.neo4j.springframework.data.core.schema.Relationship.Direction.OUTGOING

@Node("AppserviceUser")
data class AppserviceUser(
        @Id
        @Property("userId")
        val userId: String,

        @Relationship(type = "MEMBER_OF", direction = OUTGOING)
        val rooms: MutableMap<AppserviceRoom, MemberOfProperties> = HashMap()
)