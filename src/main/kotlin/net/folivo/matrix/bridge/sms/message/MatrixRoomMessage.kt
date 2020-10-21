package net.folivo.matrix.bridge.sms.message

import net.folivo.matrix.core.model.MatrixId.RoomId
import net.folivo.matrix.core.model.MatrixId.UserId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.*

@Table("matrix_room_message")
data class MatrixRoomMessage(
        @Column("room_id")
        val roomId: RoomId,
        @Column("body")
        val body: String,
        @Column("send_after")
        val sendAfter: Instant = Instant.now(),
        @Column("required_receiver_ids")
        val requiredReceiverIds: Set<UserId> = emptySet(),
        @Column("is_notice")
        val isNotice: Boolean = false,
        @Id
        @Column("id")
        val id: UUID? = null,
        @Version
        @Column("version")
        val version: Int = 0
)