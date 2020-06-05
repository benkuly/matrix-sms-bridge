package net.folivo.matrix.bridge.sms.handler

import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.handler.MessageContext
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.provider.SmsProvider
import net.folivo.matrix.bridge.sms.room.AppserviceRoom
import net.folivo.matrix.bridge.sms.room.AppserviceRoomRepository
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

@Service
class SendSmsService(
        private val matrixClient: MatrixClient,
        private val roomRepository: AppserviceRoomRepository,
        private val smsBridgeProperties: SmsBridgeProperties,
        private val botProperties: MatrixBotProperties,
        private val smsProvider: SmsProvider
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    // FIXME test
    fun createRoomAndSendSms(
            body: String,
            sender: String,
            receiverNumber: Long,
            roomName: String?,
            createNewRoom: Boolean,
            disableAutomaticRoomCreation: Boolean
    ): Mono<String> {
        val receiverId = "@sms_$receiverNumber:${botProperties.serverName}"
        val members = listOf(sender, receiverId)
        return roomRepository.findByMembersKeyUserIdContaining(members)
                .limitRequest(2)
                .collectList()
                .flatMap { rooms ->
                    if (rooms.size == 0 && !disableAutomaticRoomCreation || createNewRoom) {
                        matrixClient.roomsApi.createRoom(name = roomName, invite = members)
                                .delayUntil { roomId ->
                                    matrixClient.roomsApi.getJoinedMembers(roomId)
                                            .flatMap {
                                                if (it.joined.keys.contains(receiverId)) {
                                                    Mono.just(roomId)
                                                } else {
                                                    Mono.error(
                                                            MatrixServerException(
                                                                    NOT_FOUND,
                                                                    ErrorResponse(
                                                                            "NET_FOLIVO.NOT_FOUND",
                                                                            "Receiver $receiverId didn't join the room $roomId yet."
                                                                    )
                                                            )
                                                    )
                                                }
                                            }.retryWhen(Retry.backoff(5, Duration.ofMillis(500)))
                                }
                                .flatMap { roomId ->
                                    matrixClient.roomsApi.sendRoomEvent(
                                            roomId = roomId,
                                            eventContent = TextMessageEventContent(
                                                    smsBridgeProperties.templates.defaultRoomIncomingMessage
                                                            .replace("{sender}", sender)
                                                            .replace("{body}", body)
                                            )
                                    )
                                }.then(Mono.empty())// FIXME
                    } else if (rooms.size == 1) {
                        matrixClient.roomsApi.sendRoomEvent(
                                roomId = rooms[0].roomId,
                                eventContent = TextMessageEventContent(
                                        smsBridgeProperties.templates.defaultRoomIncomingMessage
                                                .replace("{sender}", sender)
                                                .replace("{body}", body)
                                )
                        ).then(Mono.empty())// FIXME
                    } else if (disableAutomaticRoomCreation) {
                        Mono.just("false")// FIXME
                    } else if (!createNewRoom) {
                        Mono.just("")// FIXME
                    } else {
                        Mono.just("")// FIXME
                    }
                }
    }

    fun sendSms(
            room: AppserviceRoom,
            body: String,
            sender: String,
            context: MessageContext,
            isTextMessage: Boolean
    ): Mono<Void> {
        return Flux.fromIterable(room.members.entries)
                .filter { it.key.userId != sender }
                .map {
                    Triple(it.key, it.value, it.key.userId.removePrefix("@sms_").substringBefore(":"))
                }
                .filter { it.third.matches(Regex("[0-9]{6,15}")) }
                .flatMap { (member, memberOfProps, receiver) ->
                    if (isTextMessage) {
                        LOG.debug("send SMS from ${room.roomId} to +$receiver")
                        sendSms(
                                sender = sender,
                                receiver = receiver,
                                body = body,
                                mappingToken = memberOfProps.mappingToken
                        )
                                .onErrorResume {
                                    LOG.error(
                                            "Could not send sms from room ${room.roomId} and $sender with body '$body'. " +
                                            "This should be handled, e.g. by queuing messages.", it
                                    )
                                    context.answer(
                                            NoticeMessageEventContent(
                                                    smsBridgeProperties.templates.sendSmsError
                                            ),
                                            asUserId = member.userId
                                    ).then()
                                }
                    } else {
                        LOG.debug("cannot SMS from ${room.roomId} to +$receiver because of incompatible message type")
                        context.answer(
                                NoticeMessageEventContent(
                                        smsBridgeProperties.templates.sendSmsIncompatibleMessage
                                ),
                                asUserId = member.userId
                        ).then()
                    }
                }
                .then()
    }

    // FIXME test
    fun sendSms(sender: String, receiver: String, body: String, mappingToken: Int): Mono<Void> {
        val templateBody = smsBridgeProperties.templates.outgoingMessage
                .replace("{sender}", sender)
                .replace("{body}", body)
                .replace("{token}", "#$mappingToken")
        return smsProvider.sendSms(receiver = "+$receiver", body = templateBody)
    }
}