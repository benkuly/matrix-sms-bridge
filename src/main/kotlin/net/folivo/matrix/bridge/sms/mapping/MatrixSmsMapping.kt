package net.folivo.matrix.bridge.sms.mapping

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table


@Table("matrix_sms_mapping")
data class MatrixSmsMapping(
    @Id
    @Column("membership_id")
    val membershipId: String,
    @Column("mapping_token")
    val mappingToken: Int,
    @Version
    @Column("version")
    val version: Int = 0
)