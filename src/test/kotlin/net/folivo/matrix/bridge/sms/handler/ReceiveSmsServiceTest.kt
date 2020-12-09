package net.folivo.matrix.bridge.sms.handler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.*
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bot.room.MatrixRoomService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.mapping.MatrixSmsMappingService
import net.folivo.matrix.bridge.sms.message.MatrixMessageService
import net.folivo.matrix.core.model.MatrixId.*
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient

class ReceiveSmsServiceTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val matrixClientMock: MatrixClient = mockk()
        val mappingServiceMock: MatrixSmsMappingService = mockk()
        val messageServiceMock: MatrixMessageService = mockk(relaxed = true)
        val membershipServiceMock: MatrixMembershipService = mockk()
        val roomServiceMock: MatrixRoomService = mockk(relaxed = true)
        val matrixBotPropertiesMock: MatrixBotProperties = mockk()
        val smsBridgePropertiesMock: SmsBridgeProperties = mockk {
            every { templates.defaultRoomIncomingMessage } returns "{sender} wrote {body}"
            every { templates.defaultRoomIncomingMessageWithSingleMode } returns "{sender} wrote in {roomAlias}"
            every { templates.answerInvalidTokenWithDefaultRoom } returns "invalid token with default room"
            every { templates.answerInvalidTokenWithoutDefaultRoom } returns "invalid token without default room"
        }
        val cut = ReceiveSmsService(
            matrixClientMock,
            mappingServiceMock,
            messageServiceMock,
            membershipServiceMock,
            roomServiceMock,
            matrixBotPropertiesMock,
            smsBridgePropertiesMock
        )

        beforeTest {
            every { matrixBotPropertiesMock.serverName } returns "server"
            coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any()) }
                .returns(EventId("event", "server"))
            coEvery { messageServiceMock.sendRoomMessage(any(), any()) } just Runs
        }

        describe(ReceiveSmsService::receiveSms.name) {
            val roomId = RoomId("room", "server")
            describe("mapping token was valid") {
                beforeTest { coEvery { mappingServiceMock.getRoomId(any(), any()) }.returns(roomId) }
                it("should forward message to room") {
                    cut.receiveSms("message #3", "+111111").shouldBeNull()
                    coVerify {
                        matrixClientMock.roomsApi.sendRoomEvent(
                            roomId,
                            match<TextMessageEventContent> { it.body == "message" },
                            txnId = any(),
                            asUserId = UserId("sms_111111", "server")
                        )
                    }
                }
            }
            describe("mapping token was not valid") {
                beforeTest {
                    coEvery { mappingServiceMock.getRoomId(any(), any()) }.returns(null)
                }
                describe("single mode enabled") {
                    beforeTest {
                        coEvery { smsBridgePropertiesMock.singleModeEnabled }.returns(true)
                        coEvery { roomServiceMock.getRoomAlias(any())?.roomId }.returns(roomId)
                    }
                    describe("room alias not in database") {
                        val roomAliasId = RoomAliasId("sms_111111", "server")
                        beforeTest {
                            coEvery { membershipServiceMock.hasRoomOnlyManagedUsersLeft(any()) }.returns(false)
                            coEvery { roomServiceMock.getRoomAlias(roomAliasId)?.roomId }
                                .returns(null)
                            coEvery { matrixClientMock.roomsApi.getRoomAlias(roomAliasId).roomId }.returns(roomId)
                        }
                        it("should try create alias by using client-server-api") {
                            cut.receiveSms("body #123", "+111111").shouldBeNull()
                            coVerify { matrixClientMock.roomsApi.getRoomAlias(roomAliasId) }
                        }
                    }
                    describe("not only managed users in room") {
                        beforeTest { coEvery { membershipServiceMock.hasRoomOnlyManagedUsersLeft(any()) }.returns(false) }
                        it("should send message to alias room") {
                            cut.receiveSms("body #123", "+111111").shouldBeNull()
                            coVerify {
                                messageServiceMock.sendRoomMessage(
                                    match {
                                        it.roomId == roomId
                                                && it.body == "body"
                                                && it.isNotice == false
                                                && it.asUserId == UserId("sms_111111", "server")
                                    }
                                )
                            }
                        }
                    }
                    describe("only managed users in room") {
                        beforeTest { coEvery { membershipServiceMock.hasRoomOnlyManagedUsersLeft(any()) }.returns(true) }
                        describe("default room given") {
                            beforeTest {
                                coEvery { smsBridgePropertiesMock.defaultRoomId }
                                    .returns(RoomId("default", "room"))
                            }
                            it("should send notification to default room") {
                                cut.receiveSms("body #123", "+111111").shouldBe(null)
                                coVerify {
                                    matrixClientMock.roomsApi.sendRoomEvent(
                                        RoomId("default", "room"),
                                        match<TextMessageEventContent> { it.body == "+111111 wrote in #sms_111111:server" },
                                        txnId = any()
                                    )
                                }
                            }
                        }
                        describe("default room not given") {
                            beforeTest { coEvery { smsBridgePropertiesMock.defaultRoomId }.returns(null) }
                            it("should answer and do nothing") {
                                cut.receiveSms("message #3", "+111111").shouldBe("invalid token without default room")
                                coVerify(exactly = 1) {
                                    messageServiceMock.sendRoomMessage(any(), any())
                                }
                            }
                        }
                    }
                }
                describe("single mode not enabled") {
                    beforeTest { coEvery { smsBridgePropertiesMock.singleModeEnabled }.returns(false) }
                    describe("default room given") {
                        beforeTest {
                            coEvery { smsBridgePropertiesMock.defaultRoomId }.returns(RoomId("default", "room"))
                        }
                        it("should forward message to default room") {
                            cut.receiveSms("body #123", "+111111").shouldBe("invalid token with default room")
                            coVerify {
                                matrixClientMock.roomsApi.sendRoomEvent(
                                    RoomId("default", "room"),
                                    match<TextMessageEventContent> { it.body == "+111111 wrote body" },
                                    txnId = any()
                                )
                            }
                        }
                    }
                    describe("default room not given") {
                        beforeTest { coEvery { smsBridgePropertiesMock.defaultRoomId }.returns(null) }
                        it("should answer and do nothing") {
                            cut.receiveSms("message #3", "+111111").shouldBe("invalid token without default room")
                            coVerify {
                                matrixClientMock wasNot Called
                            }
                        }
                    }
                }
            }
            describe("wrong telephone number") {
                it("should have error") {
                    shouldThrow<IllegalArgumentException> {
                        cut.receiveSms("#someBody", "+0123456789 24")
                    }
                }
            }

        }
        afterTest {
            clearMocks(
                matrixClientMock,
                mappingServiceMock,
                messageServiceMock,
                membershipServiceMock,
                roomServiceMock,
                smsBridgePropertiesMock
            )
        }
    }
}