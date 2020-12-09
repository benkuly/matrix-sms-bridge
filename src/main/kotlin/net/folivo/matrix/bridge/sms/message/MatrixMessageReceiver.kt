package net.folivo.matrix.bridge.sms.message

import net.folivo.matrix.core.model.MatrixId.UserId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table

@Table("matrix_message_receiver")
data class MatrixMessageReceiver(
    @Column("room_message_id")
    val roomMessageId: Long,
    @Column("user_id")
    val userId: UserId,
    @Id
    @Column("id")
    val id: Long? = null,
    @Version
    @Column("version")
    val version: Int = 0
)