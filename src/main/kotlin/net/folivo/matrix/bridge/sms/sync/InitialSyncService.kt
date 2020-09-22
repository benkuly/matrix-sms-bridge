package net.folivo.matrix.bridge.sms.sync

import kotlinx.coroutines.runBlocking
import net.folivo.matrix.bridge.sms.room.SmsMatrixAppserviceRoomService
import net.folivo.matrix.bridge.sms.user.SmsMatrixAppserviceUserService
import net.folivo.matrix.restclient.MatrixClient
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("initialsync")
class InitialSyncService(
        private val userService: SmsMatrixAppserviceUserService,
        private val roomService: SmsMatrixAppserviceRoomService,
        private val api: MatrixClient
) : ApplicationListener<ApplicationReadyEvent> {

    companion object {
        private val LOG = LoggerFactory.getLogger(this::class.java)
    }

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        LOG.info("started initial sync")

        runBlocking {
            LOG.info("delete all users and rooms")
            roomService.deleteAllRooms()
            userService.deleteAllUsers()

            LOG.info("collect all joined rooms (of bot user)")
            roomService.syncUserAndItsRooms()

            LOG.info("finished initial sync")
        }
    }
}