package net.folivo.matrix.bridge.sms.user

import org.springframework.data.neo4j.core.schema.Property
import org.springframework.data.neo4j.core.schema.RelationshipProperties
import org.springframework.data.neo4j.core.schema.TargetNode


@RelationshipProperties
class MemberOfProperties {

    constructor() {
    }

    constructor(member: AppserviceUser, mappingToken: Int) {
        this.member = member
        this.mappingToken = Integer(mappingToken)
    }

    @TargetNode
    lateinit var member: AppserviceUser
        private set

    @Property("mappingToken")
    lateinit var mappingToken: Integer
        private set

    override fun equals(other: Any?): Boolean {
        return if (other is MemberOfProperties) {
            other.member == this.member && other.mappingToken == this.mappingToken
        } else
            super.equals(other)
    }
}