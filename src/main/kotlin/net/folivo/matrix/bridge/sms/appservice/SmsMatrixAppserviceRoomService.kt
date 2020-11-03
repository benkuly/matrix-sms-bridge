package net.folivo.matrix.bridge.sms.appservice

import net.folivo.matrix.appservice.api.room.CreateRoomParameter
import net.folivo.matrix.bot.appservice.DefaultAppserviceRoomService
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bot.util.BotServiceHelper
import net.folivo.matrix.core.model.MatrixId.RoomAliasId
import net.folivo.matrix.core.model.MatrixId.UserId
import net.folivo.matrix.core.model.events.m.room.PowerLevelsEvent.PowerLevelsEventContent
import net.folivo.matrix.restclient.api.rooms.Visibility.PRIVATE
import org.springframework.stereotype.Service

@Service
class SmsMatrixAppserviceRoomService(
        roomService: MatrixRoomService,
        helper: BotServiceHelper,
        private val botProperties: MatrixBotProperties
) : DefaultAppserviceRoomService(roomService, helper) { //FIXME test

    override suspend fun getCreateRoomParameter(roomAlias: RoomAliasId): CreateRoomParameter {
        val invitedUser = UserId(roomAlias.localpart, roomAlias.domain)
        return CreateRoomParameter(
                visibility = PRIVATE,
                powerLevelContentOverride = PowerLevelsEventContent(
                        invite = 0,
                        kick = 0,
                        events = mapOf("m.room.name" to 0, "m.room.topic" to 0),
                        users = mapOf(invitedUser to 100)
                ),
                invite = setOf(invitedUser)
        )
    }
}