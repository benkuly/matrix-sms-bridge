package net.folivo.matrix.bridge.sms.room

import net.folivo.matrix.appservice.api.room.CreateRoomParameter
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState
import net.folivo.matrix.bot.appservice.MatrixAppserviceServiceHelper
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.AppserviceUserRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class SmsMatrixAppserviceRoomService(
        private val helper: MatrixAppserviceServiceHelper,
        private val appserviceRoomRepository: AppserviceRoomRepository,
        private val appserviceUserRepository: AppserviceUserRepository
) : MatrixAppserviceRoomService {

    override fun roomExistingState(roomAlias: String): Mono<RoomExistingState> {
        return appserviceRoomRepository.findByRoomAlias(roomAlias)
                .map { RoomExistingState.EXISTS }
                .switchIfEmpty(
                        Mono.defer {
                            helper.shouldCreateRoom(roomAlias)
                                    .map { shouldCreateRoom ->
                                        if (shouldCreateRoom) {
                                            RoomExistingState.CAN_BE_CREATED
                                        } else {
                                            RoomExistingState.DOES_NOT_EXISTS
                                        }
                                    }
                        }
                )
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
                .zipWith(
                        appserviceUserRepository.findById(userId)
                                .switchIfEmpty(Mono.just(AppserviceUser(userId)))
                ).flatMap {
                    val room = it.t1
                    val user = it.t2
                    user.rooms.add(room)
                    appserviceUserRepository.save(user)
                }.then()
    }

    override fun saveRoomLeave(roomId: String, userId: String): Mono<Void> {
        return appserviceUserRepository.findById(userId)
                .flatMap { user ->
                    user.rooms.removeIf { it.roomId == roomId }
                    appserviceUserRepository.save(user)
                }.then()
    }
}