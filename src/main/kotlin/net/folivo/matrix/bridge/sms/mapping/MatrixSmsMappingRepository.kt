package net.folivo.matrix.bridge.sms.mapping

import kotlinx.coroutines.flow.Flow
import net.folivo.matrix.core.model.MatrixId.UserId
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface MatrixSmsMappingRepository : CoroutineCrudRepository<MatrixSmsMapping, String> {

    @Query(
            """
        SELECT * from matrix_sms_mapping map
        JOIN matrix_membership mem ON mem.id = map.membership_id
        WHERE mem.user_id = :userId
        ORDER BY map.mapping_token DESC
        """
    )
    fun findByUserIdSortByMappingTokenDesc(userId: UserId): Flow<MatrixSmsMapping> //FIXME

    @Query(
            """
        SELECT * from matrix_sms_mapping map
        JOIN matrix_membership mem ON mem.id = map.membership_id
        WHERE map.mapping_token = :mappingToken AND mem.user_id = :userId
        """
    )
    fun findByUserIdAndMappingToken(userId: UserId, mappingToken: Int): MatrixSmsMapping?

    suspend fun findByMembershipId(membershipId: String): MatrixSmsMapping?
}