package net.folivo.matrix.bridge.sms.provider.android

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("AndroidSmsBatch")
data class AndroidSmsBatch(
        @Column("nextBatch")
        var nextBatch: Long,
        @Id
        val id: Long,
        @Version
        var version: Long = 0
)