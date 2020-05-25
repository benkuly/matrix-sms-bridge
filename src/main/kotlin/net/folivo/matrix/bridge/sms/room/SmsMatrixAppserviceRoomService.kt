package net.folivo.matrix.bridge.sms.room

import net.folivo.matrix.appservice.api.room.CreateRoomParameter
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.AppserviceUserRepository
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class SmsMatrixAppserviceRoomService(
        private val appserviceRoomRepository: AppserviceRoomRepository,
        private val appserviceUserRepository: AppserviceUserRepository
) : MatrixAppserviceRoomService {

    override fun roomExistingState(roomAlias: String): Mono<RoomExistingState> {
        return Mono.just(DOES_NOT_EXISTS)
    }

    override fun getCreateRoomParameter(roomAlias: String): Mono<CreateRoomParameter> {
        return Mono.just(CreateRoomParameter())
    }

    override fun saveRoom(roomAlias: String, roomId: String): Mono<Void> {
        return appserviceRoomRepository.save(AppserviceRoom(roomId, roomAlias))
                .then()
    }

    override fun saveRoomJoin(roomId: String, userId: String): Mono<Void> {
        return appserviceRoomRepository.findById(roomId)
                .switchIfEmpty(appserviceRoomRepository.save(AppserviceRoom(roomId)))
                .flatMap {
                    Mono.zip(
                            Mono.just(it),
                            appserviceUserRepository.findById(userId)
                                    .switchIfEmpty(Mono.just(AppserviceUser(userId))),
                            appserviceUserRepository.findLastMappingTokenByUserId(userId)
                                    .switchIfEmpty(Mono.just(0))
                    )
                }.flatMap {
                    val room = it.t1
                    val user = it.t2
                    val mappingToken = it.t3 + 1
                    user.rooms[room] = MemberOfProperties(mappingToken)
                    appserviceUserRepository.save(user)
                }.then()
    }

    override fun saveRoomLeave(roomId: String, userId: String): Mono<Void> {
        return appserviceUserRepository.findById(userId)
                .flatMap { user ->
                    val room = user.rooms.keys.find { it.roomId == roomId }
                    user.rooms.remove(room)
                    appserviceUserRepository.save(user)
                }.then()
    }
}