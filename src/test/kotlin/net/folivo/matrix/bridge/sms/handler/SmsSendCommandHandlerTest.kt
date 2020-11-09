package net.folivo.matrix.bridge.sms.handler

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.SmsSendCommand.RoomCreationMode.*
import net.folivo.matrix.bridge.sms.message.MatrixMessage
import net.folivo.matrix.bridge.sms.message.MatrixMessageService
import net.folivo.matrix.core.model.MatrixId.*
import net.folivo.matrix.core.model.events.m.room.PowerLevelsEvent.PowerLevelsEventContent
import net.folivo.matrix.restclient.MatrixClient
import net.folivo.matrix.restclient.api.rooms.Visibility.PRIVATE
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SmsSendCommandHelperTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val botUserId = UserId("bot", "server")

        val roomServiceMock: MatrixRoomService = mockk()
        val membershipServiceMock: MatrixMembershipService = mockk()
        val messageServiceMock: MatrixMessageService = mockk()
        val matrixClientMock: MatrixClient = mockk()
        val botPropertiesMock: MatrixBotProperties = mockk {
            every { serverName } returns "server"
            every { botUserId } returns botUserId
        }
        val smsBridgePropertiesMock: SmsBridgeProperties = mockk {
            every { defaultTimeZone }.returns("Europe/Berlin")
            every { templates.botSmsSendNewRoomMessage }.returns("message {sender} {body}")
            every { templates.botSmsSendCreatedRoomAndSendMessage }.returns("create room and send message {receiverNumbers}")
            every { templates.botSmsSendCreatedRoomAndSendNoMessage }.returns("create room and send no message {receiverNumbers}")
            every { templates.botSmsSendSendMessage }.returns("send message {receiverNumbers}")
            every { templates.botSmsSendTooManyRooms }.returns("too many rooms {receiverNumbers}")
            every { templates.botSmsSendDisabledRoomCreation }.returns("disabled room creation {receiverNumbers}")
            every { templates.botSmsSendError }.returns("error {error} {receiverNumbers}")
            every { templates.botSmsSendNoMessage }.returns("no message {receiverNumbers}")
            every { templates.botSmsSendNoticeDelayedMessage }.returns("notice at {sendAfter}")
            every { templates.botSmsSendSingleModeOnlyOneTelephoneNumberAllowed }.returns("too many numbers {receiverNumbers}")
            every { templates.botSmsSendSingleModeDisabled }.returns("single disabled {receiverNumbers}")
        }

        val cut = spyk(
                SmsSendCommandHandler(
                        roomServiceMock,
                        membershipServiceMock,
                        messageServiceMock,
                        matrixClientMock,
                        botPropertiesMock,
                        smsBridgePropertiesMock
                )
        )

        val senderId = UserId("sender", "server")
        val roomId = RoomId("room", "server")
        val userId1 = UserId("sms_111111", "server")
        val userId2 = UserId("sms_222222", "server")
        val tel1 = "+111111"
        val tel2 = "+222222"
        val sendAfterInstant = Instant.now()
        val sendAfter = LocalDateTime.ofInstant(sendAfterInstant, ZoneId.of("Europe/Berlin"))

        describe(SmsSendCommandHandler::handleCommand.name) {
            beforeTest {
                coEvery { cut.sendMessageToRoomAlias(any(), any(), any(), any()) }
                        .returns("alias {receiverNumbers}")
                coEvery { cut.createRoomAndSendMessage(any(), any(), any(), any(), any()) }
                        .returns("create {receiverNumbers}")
                coEvery { cut.sendMessageToRoom(any(), any(), any(), any(), any()) }
                        .returns("send {receiverNumbers}")
            }
            describe("room creation mode is $AUTO") {
                describe("single mode is enabled and only one receiver") {
                    beforeTest {
                        every { smsBridgePropertiesMock.singleModeEnabled }.returns(true)
                        every { roomServiceMock.getRoomsByMembers(any()) }.returns(flowOf())
                    }
                    it("should send message to room alias") {
                        cut.handleCommand("body", senderId, setOf(tel1), "room name", sendAfter, AUTO)
                                .shouldBe("alias $tel1")
                        coVerify { cut.sendMessageToRoomAlias(senderId, "body", userId1, sendAfter) }
                    }
                }
                describe("single mode is disabled") {
                    beforeTest {
                        every { smsBridgePropertiesMock.singleModeEnabled }.returns(false)
                    }
                    describe("no matching room found") {
                        beforeTest { every { roomServiceMock.getRoomsByMembers(any()) }.returns(flowOf()) }
                        it("should create room and send message") {
                            cut.handleCommand("body", senderId, setOf(tel1, tel2), "room name", sendAfter, AUTO)
                                    .shouldBe("create $tel1,$tel2")
                            coVerify {
                                cut.createRoomAndSendMessage(
                                        "body", senderId, "room name",
                                        setOf(userId1, userId2), sendAfter
                                )
                            }
                        }
                    }
                    describe("one matching room found") {
                        beforeTest {
                            every { roomServiceMock.getRoomsByMembers(any()) }.returns(flowOf(
                                    mockk { every { id }.returns(roomId) }
                            ))
                        }
                        it("should send message to room") {
                            cut.handleCommand("body", senderId, setOf(tel1, tel2), "room name", sendAfter, AUTO)
                                    .shouldBe("send $tel1,$tel2")
                            coVerify {
                                cut.sendMessageToRoom(
                                        roomId, senderId, "body",
                                        setOf(userId1, userId2, senderId), sendAfter
                                )
                            }
                        }
                    }
                    describe("more then one matching room found") {
                        beforeTest {
                            every { roomServiceMock.getRoomsByMembers(any()) }.returns(flowOf(
                                    mockk { every { id }.returns(roomId) },
                                    mockk { every { id }.returns(RoomId("room2", "server")) }
                            ))
                        }
                        it("should do nothing") {
                            cut.handleCommand("body", senderId, setOf(tel1, tel2), "room name", sendAfter, AUTO)
                                    .shouldBe("too many rooms $tel1,$tel2")
                            coVerify(exactly = 0) {
                                cut.sendMessageToRoomAlias(any(), any(), any(), any())
                                cut.createRoomAndSendMessage(any(), any(), any(), any(), any())
                                cut.sendMessageToRoom(any(), any(), any(), any(), any())
                            }
                        }
                    }
                }
            }
            describe("room creation mode is $ALWAYS") {
                beforeTest {
                    every { roomServiceMock.getRoomsByMembers(any()) }.returns(flowOf(
                            mockk { every { id }.returns(roomId) }
                    ))
                }
                it("should create room and send message") {
                    cut.handleCommand("body", senderId, setOf(tel1, tel2), "room name", sendAfter, ALWAYS)
                            .shouldBe("create $tel1,$tel2")
                    coVerify {
                        cut.createRoomAndSendMessage(
                                "body", senderId, "room name",
                                setOf(userId1, userId2), sendAfter
                        )
                    }
                }
            }
            describe("room creation mode is $SINGLE") {
                beforeTest {
                    every { roomServiceMock.getRoomsByMembers(any()) }.returns(flowOf(
                            mockk { every { id }.returns(roomId) }
                    ))
                }
                describe("single mode is enabled and only one receiver") {
                    beforeTest { every { smsBridgePropertiesMock.singleModeEnabled }.returns(true) }
                    describe("one receiver given") {
                        it("should send message to room alias") {
                            cut.handleCommand("body", senderId, setOf(tel1), "room name", sendAfter, SINGLE)
                                    .shouldBe("alias $tel1")
                            coVerify { cut.sendMessageToRoomAlias(senderId, "body", userId1, sendAfter) }
                        }
                    }
                    describe("more then one receiver given") {
                        it("should do nothing") {
                            cut.handleCommand("body", senderId, setOf(tel1, tel2), "room name", sendAfter, SINGLE)
                                    .shouldBe("too many numbers $tel1,$tel2")
                            coVerify(exactly = 0) {
                                cut.sendMessageToRoomAlias(any(), any(), any(), any())
                                cut.createRoomAndSendMessage(any(), any(), any(), any(), any())
                                cut.sendMessageToRoom(any(), any(), any(), any(), any())
                            }
                        }
                    }
                }
                describe("single mode is disabled") {
                    beforeTest { every { smsBridgePropertiesMock.singleModeEnabled }.returns(false) }
                    it("should do nothing") {
                        cut.handleCommand("body", senderId, setOf(tel1), "room name", sendAfter, SINGLE)
                                .shouldBe("single disabled $tel1")
                        coVerify(exactly = 0) {
                            cut.sendMessageToRoomAlias(any(), any(), any(), any())
                            cut.createRoomAndSendMessage(any(), any(), any(), any(), any())
                            cut.sendMessageToRoom(any(), any(), any(), any(), any())
                        }
                    }
                }
            }
            describe("room creation mode is $NO") {
                describe("no matching room found") {
                    beforeTest { every { roomServiceMock.getRoomsByMembers(any()) }.returns(flowOf()) }
                    it("should do nothing") {
                        cut.handleCommand("body", senderId, setOf(tel1), "room name", sendAfter, NO)
                                .shouldBe("disabled room creation $tel1")
                        coVerify(exactly = 0) {
                            cut.sendMessageToRoomAlias(any(), any(), any(), any())
                            cut.createRoomAndSendMessage(any(), any(), any(), any(), any())
                            cut.sendMessageToRoom(any(), any(), any(), any(), any())
                        }
                    }
                }
                describe("one matching room found") {
                    beforeTest {
                        every { roomServiceMock.getRoomsByMembers(any()) }.returns(flowOf(
                                mockk { every { id }.returns(roomId) }
                        ))
                    }
                    it("should send message to room") {
                        cut.handleCommand("body", senderId, setOf(tel1), "room name", sendAfter, NO)
                                .shouldBe("send $tel1")
                        coVerify {
                            cut.sendMessageToRoom(roomId, senderId, "body", setOf(userId1, senderId), sendAfter)
                        }
                    }
                }
                describe("more then one matching room found") {
                    beforeTest {
                        every { roomServiceMock.getRoomsByMembers(any()) }.returns(flowOf(
                                mockk { every { id }.returns(roomId) },
                                mockk { every { id }.returns(RoomId("room2", "server")) }
                        ))
                    }
                    it("should do nothing") {
                        cut.handleCommand("body", senderId, setOf(tel1), "room name", sendAfter, NO)
                                .shouldBe("too many rooms $tel1")
                        coVerify(exactly = 0) {
                            cut.sendMessageToRoomAlias(any(), any(), any(), any())
                            cut.createRoomAndSendMessage(any(), any(), any(), any(), any())
                            cut.sendMessageToRoom(any(), any(), any(), any(), any())
                        }
                    }
                }
            }
        }

        describe(SmsSendCommandHandler::sendMessageToRoomAlias.name) {
            val roomAliasId = RoomAliasId("alias", "server")
            beforeTest {
                coEvery { cut.sendMessageToRoom(any(), any(), any(), any(), any()) }
                        .returns("send")
            }
            describe("room alias does exist") {
                beforeTest {
                    coEvery { roomServiceMock.getRoomAlias(roomAliasId)?.roomId }
                            .returns(roomId)
                }
                describe("sender is member of room alias") {
                    beforeTest {
                        coEvery {
                            membershipServiceMock
                                    .doesRoomContainsMembers(roomId, match { it.containsAll(setOf(senderId)) })
                        }.returns(true)
                    }
                    it("should send message to room alias") {
                        coEvery { roomServiceMock.getRoomsByMembers(any()) }.returns(flowOf())
                        cut.sendMessageToRoomAlias(senderId, "body", userId1, sendAfter)
                                .shouldBe("send")
                        coVerify {
                            cut.sendMessageToRoom(roomId, senderId, "body", setOf(userId1), sendAfter)
                        }
                    }
                }
                describe("sender is not member of room alias") {
                    beforeTest {
                        coEvery {
                            membershipServiceMock
                                    .doesRoomContainsMembers(roomId, match { it.containsAll(setOf(senderId)) })
                        }.returns(false)
                    }
                    it("should invite user and send message to room alias") {
                        coEvery {
                            roomServiceMock
                                    .getRoomsByMembers(match { it.containsAll(setOf(userId1, senderId)) })
                        }.returns(flowOf())

                        cut.sendMessageToRoomAlias(senderId, "body", userId1, sendAfter)
                                .shouldBe("send")
                        coVerify {
                            matrixClientMock.roomsApi.inviteUser(roomId, senderId)
                            cut.sendMessageToRoom(roomId, senderId, "body", setOf(userId1), sendAfter)
                        }
                    }
                }
            }
            describe("room alias does not exist") {
                beforeTest {
                    coEvery { roomServiceMock.getRoomAlias(RoomAliasId("sms_111111", "server")) }
                            .returns(null)
                }
                it("should get room alias, invite user and send message") {
                    coEvery { matrixClientMock.roomsApi.getRoomAlias(any()).roomId }.returns(roomId)
                    cut.sendMessageToRoomAlias(senderId, "body", userId1, sendAfter)
                            .shouldBe("send")
                    coVerify {
                        matrixClientMock.roomsApi.getRoomAlias(roomAliasId)
                        matrixClientMock.roomsApi.inviteUser(roomId, senderId)
                        cut.sendMessageToRoom(roomId, senderId, "body", setOf(userId1), sendAfter)
                    }
                }
            }
        }

        describe(SmsSendCommandHandler::createRoomAndSendMessage.name) {
            beforeTest {
                coEvery { cut.sendMessageToRoom(any(), any(), any(), any(), any()) }
                        .returns("send")
                coEvery { matrixClientMock.roomsApi.createRoom(allAny()) }.returns(roomId)
            }
            describe("body is null or empty") {
                it("should only create room") {
                    cut.createRoomAndSendMessage(null, senderId, "room name", setOf(userId1), sendAfter)
                            .shouldBe("create room and send no message {receiverNumbers}")
                    coVerify {
                        matrixClientMock.roomsApi.createRoom(
                                name = "room name",
                                invite = setOf(senderId, userId1),
                                visibility = PRIVATE,
                                powerLevelContentOverride = PowerLevelsEventContent(
                                        invite = 0,
                                        kick = 0,
                                        events = mapOf("m.room.name" to 0, "m.room.topic" to 0)
                                )
                        )
                    }
                }
            }
            describe("body contains message") {
                it("should create room and send message") {
                    cut.createRoomAndSendMessage("body", senderId, "room name", setOf(userId1), sendAfter)
                            .shouldBe("create room and send no message {receiverNumbers}")
                    coVerify {
                        matrixClientMock.roomsApi.createRoom(
                                name = "room name",
                                invite = setOf(senderId, userId1),
                                visibility = PRIVATE,
                                powerLevelContentOverride = PowerLevelsEventContent(
                                        invite = 0,
                                        kick = 0,
                                        events = mapOf("m.room.name" to 0, "m.room.topic" to 0)
                                )
                        )
                        cut.sendMessageToRoom(roomId, senderId, "body", setOf(userId1), sendAfter)
                    }
                }
            }
        }

        describe(SmsSendCommandHandler::sendMessageToRoom.name) {
            describe("body is null or blank") {
                it("should do nothing") {
                    cut.sendMessageToRoom(roomId, senderId, null, setOf(userId1), null)
                            .shouldBe("no message $tel1")
                    coVerify {
                        messageServiceMock wasNot Called
                    }
                }
            }
            describe("bot is not member") {
                beforeTest {
                    coEvery {
                        membershipServiceMock.doesRoomContainsMembers(
                                roomId,
                                match { it.containsAll(setOf(botUserId)) })
                    }.returns(false)
                }
                it("should invite bot with first user and send message") {
                    cut.sendMessageToRoom(roomId, senderId, "body", setOf(userId1, userId2), sendAfter)
                            .shouldBe("send message $tel1,$tel2")
                    coVerify {
                        matrixClientMock.roomsApi.inviteUser(roomId, botUserId, userId1)
                        messageServiceMock.sendRoomMessage(
                                MatrixMessage(
                                        roomId = roomId,
                                        body = "message @sender:server body",
                                        sendAfter = sendAfterInstant
                                ), setOf(userId1, userId2)
                        )
                    }
                }
            }
            describe("bot is member") {
                beforeTest {
                    coEvery {
                        membershipServiceMock.doesRoomContainsMembers(
                                roomId,
                                match { it.containsAll(setOf(botUserId)) })
                    }.returns(true)
                }
                it("should send message") {
                    cut.sendMessageToRoom(roomId, senderId, "body", setOf(userId1, userId2), sendAfter)
                            .shouldBe("send message $tel1,$tel2")
                    coVerify {
                        messageServiceMock.sendRoomMessage(
                                MatrixMessage(
                                        roomId = roomId,
                                        body = "message @sender:server body",
                                        sendAfter = sendAfterInstant
                                ), setOf(userId1, userId2)
                        )
                    }
                }
                describe("send after is more then 15 seconds in future") {
                    it("should notify user about this") {
                        cut.sendMessageToRoom(
                                roomId,
                                senderId,
                                "body",
                                setOf(userId1, userId2),
                                sendAfter.plusSeconds(2424)
                        )
                                .shouldBe("send message $tel1,$tel2")
                        coVerify {
                            messageServiceMock.sendRoomMessage(
                                    MatrixMessage(
                                            roomId = roomId,
                                            body = "notice at bla",
                                            isNotice = true
                                    ), setOf(userId1, userId2)
                            )
                            messageServiceMock.sendRoomMessage(
                                    MatrixMessage(
                                            roomId = roomId,
                                            body = "message @sender:server body",
                                            sendAfter = sendAfterInstant.plusSeconds(2424)
                                    ), setOf(userId1, userId2)
                            )
                        }
                    }
                }
            }
        }
        describe("some error occurs") {
            beforeTest {
                every { roomServiceMock.getRoomsByMembers(any()) }.throws(RuntimeException("mimimi"))
            }
            it("should catch exception and notify user") {
                cut.handleCommand("body", senderId, setOf(tel1), "room name", sendAfter, AUTO)
                        .shouldBe("error mimimi $tel1")
            }
        }

        afterTest {
            clearMocks(
                    cut,
                    roomServiceMock,
                    membershipServiceMock,
                    messageServiceMock,
                    matrixClientMock,
                    smsBridgePropertiesMock
            )
        }
    }
}