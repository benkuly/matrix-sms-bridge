package net.folivo.matrix.bridge.sms.message

import kotlinx.coroutines.flow.collect
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class MatrixRoomMessageService( //FIXME test
        private val messageRepository: MatrixRoomMessageRepository,
        private val membershipService: MatrixMembershipService,
        private val matrixClient: MatrixClient
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    suspend fun sendRoomMessage(message: MatrixRoomMessage) {
        val isNew = message.id == null
        if (Instant.now().isAfter(message.sendAfter)) {
            val roomId = message.roomId
            val containsReceivers = membershipService.doesRoomContainsMembers(roomId, message.requiredReceiverIds)
            if (containsReceivers) {
                try {
                    matrixClient.roomsApi.sendRoomEvent(
                            roomId = roomId,
                            eventContent = if (message.isNotice) NoticeMessageEventContent(message.body)
                            else TextMessageEventContent(message.body)
                    )
                    LOG.debug("sent cached message to room $roomId")
                    messageRepository.delete(message)
                } catch (error: Throwable) {
                    LOG.debug(
                            "Could not send cached message to room $roomId. This happens e.g. when the bot was kicked " +
                            "out of the room, before the required receivers did join. Error: ${error.message}"
                    )
                    if (message.sendAfter.until(Instant.now(), ChronoUnit.DAYS) > 3) {
                        LOG.warn(
                                "We have cached messages for the room $roomId, but sending the message " +
                                "didn't worked since 3 days. " +
                                "This usually should never happen! The message will now be deleted."
                        )
                        messageRepository.delete(message)
                        // TODO directly notify user
                    }
                }
            } else if (!isNew && message.sendAfter.until(Instant.now(), ChronoUnit.DAYS) > 3) {
                LOG.warn(
                        "We have cached messages for the room $roomId, but the required receivers " +
                        "${message.requiredReceiverIds.joinToString()} didn't join since 3 days. " +
                        "This usually should never happen! The message will now be deleted."
                )
                messageRepository.delete(message)
                // TODO directly notify user
            } else if (isNew) {
                messageRepository.save(message.copy(sendAfter = Instant.now()))
            } else {
                LOG.debug("wait for required receivers to join")
            }
        } else if (isNew) { //FIXME test
            messageRepository.save(message)
        }
    }

    suspend fun processMessageQueue() {
        messageRepository.findAll().collect { sendRoomMessage(it) }
    }
}