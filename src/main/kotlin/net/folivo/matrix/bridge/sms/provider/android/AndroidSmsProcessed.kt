package net.folivo.matrix.bridge.sms.provider.android

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("android_sms_processed")
data class AndroidSmsProcessed(
        @Id
        @Column("id")
        val id: Long,
        @Column("last_processed_id")
        var lastProcessedId: Int,
        @Version
        @Column("version")
        var version: Int = 0
)