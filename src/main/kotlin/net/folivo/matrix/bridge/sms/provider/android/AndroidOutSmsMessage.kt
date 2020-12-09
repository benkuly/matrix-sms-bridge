package net.folivo.matrix.bridge.sms.provider.android

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("android_out_sms_message")
data class AndroidOutSmsMessage(
    @Column("receiver")
    val receiver: String,
    @Column("body")
    val body: String,
    @Id
    @Column("id")
    val id: Long? = null,
    @Version
    @Column("version")
    var version: Int = 0
)