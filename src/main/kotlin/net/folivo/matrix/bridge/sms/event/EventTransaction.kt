package net.folivo.matrix.bridge.sms.event

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.util.*

@Table("EventTransaction")
data class EventTransaction(
        @Column("tnxId")
        var tnxId: String,
        @Column("eventIdElseType")
        var eventIdElseType: String,
        @Id
        val id: UUID = UUID.randomUUID(),
        @Version
        val version: Int = 1
)