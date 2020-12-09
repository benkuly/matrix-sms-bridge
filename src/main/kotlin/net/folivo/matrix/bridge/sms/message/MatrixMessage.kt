package net.folivo.matrix.bridge.sms.message

import net.folivo.matrix.core.model.MatrixId.RoomId
import net.folivo.matrix.core.model.MatrixId.UserId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("matrix_message")
data class MatrixMessage(
    @Column("room_id")
    val roomId: RoomId,
    @Column("body")
    val body: String,
    @Column("send_after")
    val sendAfter: Instant = Instant.now(),
    @Column("is_notice")
    val isNotice: Boolean = false,
    @Column("as_user_id")
    val asUserId: UserId? = null,
    @Id
    @Column("id")
    val id: Long? = null,
    @Version
    @Column("version")
    val version: Int = 0
)