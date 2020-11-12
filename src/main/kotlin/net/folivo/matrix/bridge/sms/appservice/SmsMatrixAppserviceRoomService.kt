package net.folivo.matrix.bridge.sms.appservice

import net.folivo.matrix.appservice.api.room.AppserviceRoomService.RoomExistingState
import net.folivo.matrix.appservice.api.room.AppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.appservice.api.room.CreateRoomParameter
import net.folivo.matrix.bot.appservice.DefaultAppserviceRoomService
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bot.util.BotServiceHelper
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.core.model.MatrixId.RoomAliasId
import net.folivo.matrix.core.model.MatrixId.UserId
import net.folivo.matrix.core.model.events.m.room.PowerLevelsEvent.PowerLevelsEventContent
import net.folivo.matrix.restclient.api.rooms.Visibility.PRIVATE
import org.springframework.stereotype.Service

@Service
class SmsMatrixAppserviceRoomService(
        roomService: MatrixRoomService,
        helper: BotServiceHelper,
        private val botProperties: MatrixBotProperties,
        private val bridgeProperties: SmsBridgeProperties
) : DefaultAppserviceRoomService(roomService, helper) {

    override suspend fun getCreateRoomParameter(roomAlias: RoomAliasId): CreateRoomParameter {
        val invitedUser = UserId(roomAlias.localpart, botProperties.serverName)
        return CreateRoomParameter(
                visibility = PRIVATE,
                powerLevelContentOverride = PowerLevelsEventContent(
                        invite = 0,
                        kick = 0,
                        events = mapOf("m.room.name" to 0, "m.room.topic" to 0),
                        users = mapOf(invitedUser to 100, botProperties.botUserId to 100)
                ),
                invite = setOf(invitedUser)
        )
    }

    override suspend fun roomExistingState(roomAlias: RoomAliasId): RoomExistingState {
        return if (!bridgeProperties.singleModeEnabled) DOES_NOT_EXISTS
        else super.roomExistingState(roomAlias)
    }
}