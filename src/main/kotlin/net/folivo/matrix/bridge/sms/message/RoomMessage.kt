package net.folivo.matrix.bridge.sms.message

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

@Table("RoomMessage")
data class RoomMessage(
        @Column("fk_RoomMessage_AppserviceRoom")
        val roomId: String,
        @Column("body")
        var body: String,
        @Column("sendAfter")
        var sendAfter: Instant = Instant.now(),
        @Column("requiredReceiverIds")
        var requiredReceiverIds: Set<String> = emptySet(),
        @Column("isNotice")
        val isNotice: Boolean = false,
        @Id
        val id: UUID = UUID.randomUUID(),
        @Version
        val version: Int = 1
)