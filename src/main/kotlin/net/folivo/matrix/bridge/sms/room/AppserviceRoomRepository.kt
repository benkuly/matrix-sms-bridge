package net.folivo.matrix.bridge.sms.room

import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository

@Repository
interface AppserviceRoomRepository : ReactiveCrudRepository<AppserviceRoom, String> {

}