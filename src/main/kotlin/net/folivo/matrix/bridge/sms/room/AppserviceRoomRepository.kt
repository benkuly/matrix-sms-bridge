package net.folivo.matrix.bridge.sms.room

import net.folivo.matrix.bridge.sms.user.AppserviceUser
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface AppserviceRoomRepository : ReactiveCrudRepository<AppserviceRoom, String> {

    fun findByMappingTokenAndMembersUserId(mappingToken: Int, userId: String): Mono<AppserviceUser>
}