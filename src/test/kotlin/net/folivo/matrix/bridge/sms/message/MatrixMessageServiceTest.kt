package net.folivo.matrix.bridge.sms.message

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bot.user.MatrixUserService
import net.folivo.matrix.core.model.MatrixId.*
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import java.time.Instant
import java.time.temporal.ChronoUnit

class MatrixMessageServiceTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val messageRepositoryMock: MatrixMessageRepository = mockk()
        val messageReceiverRepositoryMock: MatrixMessageReceiverRepository = mockk()
        val membershipServiceMock: MatrixMembershipService = mockk()
        val userServiceMock: MatrixUserService = mockk()
        val matrixClientMock: MatrixClient = mockk()

        val cut = spyk(
            MatrixMessageService(
                messageRepositoryMock,
                messageReceiverRepositoryMock,
                membershipServiceMock,
                userServiceMock,
                matrixClientMock
            )
        )

        val roomId = RoomId("room", "server")
        val userId1 = UserId("user1", "server")
        val userId2 = UserId("user2", "server")

        describe(MatrixMessageService::sendRoomMessage.name) {
            beforeTest {
                coEvery { cut.deleteMessage(any()) } just Runs
                coEvery { cut.saveMessageAndReceivers(any(), any()) } just Runs
            }
            describe("check required users") {
                beforeTest { coEvery { membershipServiceMock.doesRoomContainsMembers(any(), any()) }.returns(false) }
                it("should use set of required users and sender when sender is given") {
                    val message =
                        MatrixMessage(roomId, "body", Instant.now().minusSeconds(400000), asUserId = userId1)
                    cut.sendRoomMessage(message, setOf(userId2))
                    coVerify { membershipServiceMock.doesRoomContainsMembers(roomId, setOf(userId1, userId2)) }
                }
                it("should use set of required users when sender is not given") {
                    val message =
                        MatrixMessage(roomId, "body", Instant.now().minusSeconds(400000))
                    cut.sendRoomMessage(message, setOf(userId2))
                    coVerify { membershipServiceMock.doesRoomContainsMembers(roomId, setOf(userId2)) }
                }
            }
            describe("message send after is after now") {
                describe("room contains receivers") {
                    beforeTest {
                        coEvery { membershipServiceMock.doesRoomContainsMembers(any(), any()) }
                            .returns(true)
                    }
                    describe("sending message fails") {
                        beforeTest {
                            coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                                .throws(RuntimeException())
                        }
                        describe("message is older then three days") {
                            val message = MatrixMessage(
                                roomId,
                                "body",
                                sendAfter = Instant.now().minusSeconds(400000),
                                id = 2
                            )
                            it("should delete message") {
                                cut.sendRoomMessage(message, setOf(userId1))
                                coVerify { cut.deleteMessage(message) }
                            }
                        }
                        describe("message is not old") {
                            val message = MatrixMessage(roomId, "body", sendAfter = Instant.now().minusSeconds(2424))
                            it("should not delete message") {
                                cut.sendRoomMessage(message, setOf(userId1))
                                coVerify(exactly = 0) {
                                    cut.deleteMessage(any())
                                }
                            }
                        }
                    }
                    describe("message is notice") {
                        val message = MatrixMessage(
                            roomId,
                            "body",
                            isNotice = true,
                            sendAfter = Instant.now().minusSeconds(2424)
                        )
                        it("should send message and delete message from database") {
                            coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                                .returns(EventId("event", "server"))
                            cut.sendRoomMessage(message, setOf(userId1))
                            coVerify {
                                matrixClientMock.roomsApi.sendRoomEvent(
                                    roomId,
                                    match<NoticeMessageEventContent> { it.body == "body" },
                                    txnId = any()
                                )
                                cut.deleteMessage(message)
                            }
                        }
                    }
                    describe("message is not notice") {
                        val message = MatrixMessage(
                            roomId,
                            "body",
                            isNotice = false,
                            sendAfter = Instant.now().minusSeconds(2424)
                        )
                        it("should send message and delete message from database") {
                            coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                                .returns(EventId("event", "server"))
                            cut.sendRoomMessage(message, setOf(userId1))
                            coVerify {
                                matrixClientMock.roomsApi.sendRoomEvent(
                                    roomId,
                                    match<TextMessageEventContent> { it.body == "body" },
                                    txnId = any()
                                )
                                cut.deleteMessage(message)
                            }
                        }
                    }
                }
                describe("room does not contain receivers") {
                    beforeTest {
                        coEvery { membershipServiceMock.doesRoomContainsMembers(any(), any()) }
                            .returns(false)
                    }
                    describe("message is new") {
                        val message = MatrixMessage(roomId, "body", sendAfter = Instant.now().minusSeconds(400000))
                        it("should change send after to now and save message") {
                            cut.sendRoomMessage(message, setOf(userId1))
                            coVerify {
                                cut.saveMessageAndReceivers(
                                    match {
                                        it.body == "body"
                                                && it.sendAfter.until(Instant.now(), ChronoUnit.MINUTES) < 24
                                    },
                                    setOf(userId1)
                                )
                            }
                        }
                    }
                    describe("message is older then three days and not new") {
                        val message = MatrixMessage(
                            roomId,
                            "body",
                            sendAfter = Instant.now().minusSeconds(400000),
                            id = 2
                        )
                        it("should delete message") {
                            cut.sendRoomMessage(message, setOf(userId1))
                            coVerify { cut.deleteMessage(message) }
                        }
                    }
                    describe("message is not new") {
                        val message = MatrixMessage(roomId, "body", sendAfter = Instant.now().minusSeconds(2424))
                        it("should do nothing") {
                            cut.sendRoomMessage(message, setOf(userId1))
                            coVerify(exactly = 0) {
                                cut.deleteMessage(any())
                                matrixClientMock wasNot Called
                            }
                        }
                    }
                }
            }
            describe("message send after is before now") {
                val message = MatrixMessage(roomId, "body", sendAfter = Instant.now().plusSeconds(2424))
                it("should save when message is new") {
                    cut.sendRoomMessage(message, setOf(userId1))
                    coVerify { cut.saveMessageAndReceivers(message, setOf(userId1)) }
                }
                describe("message is not new") {
                    cut.sendRoomMessage(message, setOf(userId1))
                    coVerify(exactly = 0) { cut.saveMessageAndReceivers(message, setOf()) }
                }
            }
        }
        describe(MatrixMessageService::saveMessageAndReceivers.name) {
            it("should save message and its receivers") {
                val senderId = UserId("sender", "server")
                val message = MatrixMessage(roomId, "body", asUserId = senderId)
                coEvery { messageRepositoryMock.save(message) }.returns(message.copy(id = 24))
                coEvery { messageReceiverRepositoryMock.save(any()) }.returns(mockk())
                coEvery { userServiceMock.getOrCreateUser(any()) }.returns(mockk())
                cut.saveMessageAndReceivers(message, setOf(userId1, userId2))
                coVerifyAll {
                    messageReceiverRepositoryMock.save(MatrixMessageReceiver(24, userId1))
                    messageReceiverRepositoryMock.save(MatrixMessageReceiver(24, userId2))
                    userServiceMock.getOrCreateUser(senderId)
                    userServiceMock.getOrCreateUser(userId1)
                    userServiceMock.getOrCreateUser(userId2)
                }
            }
        }
        describe(MatrixMessageService::deleteMessage.name) {
            beforeTest { coEvery { messageRepositoryMock.delete(any()) } just Runs }
            it("should delete message, when saved") {
                val message = MatrixMessage(roomId, "body", id = 1)
                cut.deleteMessage(message)
                coVerify { messageRepositoryMock.delete(message) }
            }
            it("should not delete message, when new") {
                val message = MatrixMessage(roomId, "body")
                cut.deleteMessage(message)
                coVerify(exactly = 0) { messageRepositoryMock.delete(message) }
            }
        }
        describe(MatrixMessageService::processMessageQueue.name) {
            it("should process all saved messages") {
                val message1 = MatrixMessage(roomId, "body", id = 1)
                val message2 = MatrixMessage(roomId, "body", id = 2)
                every { messageRepositoryMock.findAll() }.returns(flowOf(message1, message2))
                every { messageReceiverRepositoryMock.findByRoomMessageId(1) }
                    .returns(flowOf(MatrixMessageReceiver(1, userId1)))
                every { messageReceiverRepositoryMock.findByRoomMessageId(2) }
                    .returns(flowOf(MatrixMessageReceiver(2, userId1), MatrixMessageReceiver(2, userId2)))
                coEvery { cut.sendRoomMessage(any(), any()) } just Runs
                cut.processMessageQueue()
                coVerify {
                    cut.sendRoomMessage(message1, setOf(userId1))
                    cut.sendRoomMessage(message2, setOf(userId1, userId2))
                }
            }
        }

        afterTest {
            clearMocks(
                cut,
                messageRepositoryMock,
                messageReceiverRepositoryMock,
                membershipServiceMock,
                matrixClientMock
            )
        }
    }
}