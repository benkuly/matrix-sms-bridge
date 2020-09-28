package net.folivo.matrix.bridge.sms.room

import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import org.neo4j.springframework.data.core.schema.Id
import org.neo4j.springframework.data.core.schema.Node
import org.neo4j.springframework.data.core.schema.Relationship
import org.neo4j.springframework.data.core.schema.Relationship.Direction.OUTGOING

@Node("AppserviceRoom")
data class AppserviceRoom(
        @Id
        val roomId: String,

        @Relationship(type = "MEMBER_OF", direction = OUTGOING)
        val members: MutableMap<AppserviceUser, MemberOfProperties> = HashMap()
)