package net.folivo.matrix.bridge.sms.user

import org.springframework.data.neo4j.core.schema.Id
import org.springframework.data.neo4j.core.schema.Node
import org.springframework.data.neo4j.core.schema.Property


@Node("AppserviceUser")
data class AppserviceUser(
        @Id
        val userId: String,

        @Property("isManaged")
        val isManaged: Boolean
)