package net.folivo.matrix.bridge.sms.user

import org.neo4j.springframework.data.core.schema.Id
import org.neo4j.springframework.data.core.schema.Node

@Node("AppserviceUser")
data class AppserviceUser(
        @Id
        val userId: String,

        val isManaged: Boolean
)