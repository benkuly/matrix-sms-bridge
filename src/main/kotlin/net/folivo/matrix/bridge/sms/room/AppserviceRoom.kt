package net.folivo.matrix.bridge.sms.room

import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import org.neo4j.springframework.data.core.schema.Id
import org.neo4j.springframework.data.core.schema.Node
import org.neo4j.springframework.data.core.schema.Property
import org.neo4j.springframework.data.core.schema.Relationship
import org.neo4j.springframework.data.core.schema.Relationship.Direction.INCOMING

@Node("AppserviceRoom")
data class AppserviceRoom(
        @Id
        @Property("roomId")
        val roomId: String,

        @Property("roomAlias")
        val roomAlias: String? = null,

        @Relationship(type = "MEMBER_OF", direction = INCOMING)
        val members: MutableMap<AppserviceUser, MemberOfProperties> = HashMap()
)