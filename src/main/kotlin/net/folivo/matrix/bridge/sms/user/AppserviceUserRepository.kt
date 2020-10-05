package net.folivo.matrix.bridge.sms.user

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux

@Repository
interface AppserviceUserRepository : ReactiveCrudRepository<AppserviceUser, String> {

    @Query(
            """
            SELECT * FROM AppserviceUser a 
            JOIN Membership m ON m.fk_Membership_AppserviceUser = a.id 
            WHERE m.fk_Membership_AppserviceRoom = :roomId
            """
    )
    fun findByRoomId(roomId: String): Flux<AppserviceUser>
}