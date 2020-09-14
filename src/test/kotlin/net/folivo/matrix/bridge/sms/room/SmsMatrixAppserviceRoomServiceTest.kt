package net.folivo.matrix.bridge.sms.room

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.bridge.sms.user.MemberOfProperties
import net.folivo.matrix.bridge.sms.user.SmsMatrixAppserviceUserService
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import net.folivo.matrix.restclient.api.rooms.GetJoinedMembersResponse
import net.folivo.matrix.restclient.api.rooms.GetJoinedMembersResponse.RoomMember
import org.assertj.core.api.Assertions.assertThat
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.test.util.ReflectionTestUtils
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit

class SmsMatrixAppserviceRoomServizceTest : DescribeSpec() {
    init {
        val roomRepositoryMock: AppserviceRoomRepository = mockk()
        val userServiceMock: SmsMatrixAppserviceUserService = mockk()
        val messageRepositoryMock: RoomMessageRepository = mockk()
        val botPropertiesMock: MatrixBotProperties = mockk()
        val smsBridgePropertiesMock: SmsBridgeProperties = mockk()
        val matrixClientMock: MatrixClient = mockk()

        var cut: SmsMatrixAppserviceRoomService = spyk(
                SmsMatrixAppserviceRoomService(
                        roomRepositoryMock,
                        messageRepositoryMock,
                        userServiceMock,
                        matrixClientMock,
                        botPropertiesMock,
                        smsBridgePropertiesMock
                )
        )


        beforeTest {
            every { botPropertiesMock.username }.returns("bot")
            every { botPropertiesMock.serverName }.returns("someServer")
            every { messageRepositoryMock.delete(any()) }.returns(Mono.empty())
        }

        afterTest {
            clearMocks(
                    roomRepositoryMock,
                    userServiceMock,
                    messageRepositoryMock,
                    botPropertiesMock,
                    smsBridgePropertiesMock,
                    matrixClientMock
            )
        }

        describe(SmsMatrixAppserviceRoomService::roomExistingState.name) {
            it("should always be DOES_NOT_EXIST") {
                val result = runBlocking {
                    cut.roomExistingState("someRoomAlias")
                }
                assertThat(result).isEqualTo(DOES_NOT_EXISTS)
            }
        }
        describe(SmsMatrixAppserviceRoomService::saveRoom.name) {
            it("should not save room in database") {
                runBlocking {
                    cut.saveRoom("someRoomAlias", "someRoomId")
                }
                verify { roomRepositoryMock wasNot Called }
            }
        }
        describe(SmsMatrixAppserviceRoomService::saveRoomJoin.name) {
            it("should save user join in database") {
                val room = AppserviceRoom("someRoomId", mutableMapOf(mockk<AppserviceUser>() to mockk()))
                val user = AppserviceUser("someUserId", true)
                every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
                coEvery { userServiceMock.getUser("someUserId") }.returns(user)
                coEvery { userServiceMock.getLastMappingToken("someUserId") }.returns(23)
                every { roomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))

                runBlocking {
                    cut.saveRoomJoin("someRoomId", "someUserId")
                }

                verify {
                    roomRepositoryMock.save<AppserviceRoom>(match {
                        it.members.contains(user)
                        && it.members[user] == MemberOfProperties(24)
                    })
                }
            }
            describe("if room does not exists yet") {
                it("should save user join to room") {
                    val user = AppserviceUser("someUserId", true)
                    val existingUser = AppserviceUser("someExistingUserId", false)
                    val roomWithoutMember = AppserviceRoom("someRoomId")
                    val roomWithMember = AppserviceRoom(
                            "someRoomId", mutableMapOf(
                            existingUser to MemberOfProperties(1),
                            user to MemberOfProperties(1)
                    )
                    )
                    coEvery { userServiceMock.getUser("someExistingUserId") }.returns(existingUser)
                    coEvery { userServiceMock.getUser("someUserId") }.returns(user)
                    coEvery { userServiceMock.getLastMappingToken("someExistingUserId") }.returns(0)
                    coEvery { userServiceMock.getLastMappingToken("someUserId") }.returns(0)
                    every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.empty())
                    every { roomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(roomWithoutMember))
                    coEvery { matrixClientMock.roomsApi.getJoinedMembers(allAny()) }.returns(
                            GetJoinedMembersResponse(
                                    mapOf(
                                            "someExistingUserId" to mockk(),
                                            "someUserId" to mockk()
                                    )
                            )
                    )

                    runBlocking {
                        cut.saveRoomJoin("someRoomId", "someUserId")
                    }

                    verify {
                        roomRepositoryMock.save<AppserviceRoom>(roomWithMember)
                    }
                }
            }
        }
        describe(SmsMatrixAppserviceRoomService::saveRoomLeave.name) {
            val user1 = AppserviceUser("someUserId1", false)
            val user2 = AppserviceUser("someUserId2", false)
            val user3 = AppserviceUser("someUserId3", true)
            it("should save user room leave in database") {
                val room = AppserviceRoom(
                        "someRoomId", mutableMapOf(
                        user1 to MemberOfProperties(1),
                        user2 to MemberOfProperties(1),
                        user3 to MemberOfProperties(1)
                )
                )
                every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
                every { roomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))

                runBlocking {
                    cut.saveRoomLeave("someRoomId", "someUserId1")
                }

                verify {
                    roomRepositoryMock.save<AppserviceRoom>(match {
                        it.members.keys.containsAll(listOf(user2, user3))
                    })
                }
            }
            describe("all users are managed users") {
                it("should save user room leave in database and leave room") {
                    val user4 = AppserviceUser("@bot:someServer", true)
                    val room = AppserviceRoom(
                            "someRoomId", mutableMapOf(
                            user1 to MemberOfProperties(1),
                            user3 to MemberOfProperties(1),
                            user4 to MemberOfProperties(1)
                    )
                    )
                    every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
                    coEvery { matrixClientMock.roomsApi.leaveRoom(allAny()) } just Runs
                    every { roomRepositoryMock.delete(any()) }.returns(Mono.empty())

                    runBlocking {
                        cut.saveRoomLeave("someRoomId", "someUserId1")
                    }

                    verifyAll {
                        roomRepositoryMock.findById("someRoomId")
                        roomRepositoryMock.delete(match {
                            it.members.keys.containsAll(listOf(user3, user4))
                        })
                    }
                    coVerifyAll {
                        matrixClientMock.roomsApi.leaveRoom("someRoomId", "someUserId3")
                        matrixClientMock.roomsApi.leaveRoom("someRoomId", null)
                    }
                }
            }
        }
        describe(SmsMatrixAppserviceRoomService::getRoom.name) {
            describe("matching token given") {
                it("should get room") {
                    val room = AppserviceRoom("someRoomId2")
                    every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)
                    coEvery { userServiceMock.getUser("someUserId") }
                            .returns(
                                    AppserviceUser(
                                            "someUserId", true, mutableMapOf(
                                            AppserviceRoom("someRoomId1") to MemberOfProperties(12),
                                            room to MemberOfProperties(24)
                                    )
                                    )

                            )
                    val result = runBlocking { cut.getRoom("someUserId", 24) }
                    assertThat(result).isEqualTo(room)
                }
            }
            describe("no matching token given") {
                it("should get first and only room when allowed") {
                    val room = AppserviceRoom("someRoomId1")

                    every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(true)
                    coEvery { userServiceMock.getUser("someUserId") }
                            .returns(
                                    AppserviceUser(
                                            "someUserId", true, mutableMapOf(
                                            room to MemberOfProperties(12)
                                    )
                                    )
                            )
                    val result = runBlocking { cut.getRoom("someUserId", 24) }
                    assertThat(result).isEqualTo(room)
                }
                it("should not get room") {
                    every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)

                    coEvery { userServiceMock.getUser("someUserId") }
                            .returns(
                                    AppserviceUser(
                                            "someUserId", true, mutableMapOf(
                                            AppserviceRoom("someRoomId1") to MemberOfProperties(12)
                                    )
                                    )
                            )
                    val result = runBlocking { cut.getRoom("someUserId", 24) }
                    assertThat(result).isNull()
                }
                it("should not get room when token is null") {
                    every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)

                    coEvery { userServiceMock.getUser("someUserId") }
                            .returns(
                                    AppserviceUser(
                                            "someUserId", true, mutableMapOf(
                                            AppserviceRoom("someRoomId1") to MemberOfProperties(12)
                                    )
                                    )
                            )
                    val result = runBlocking { cut.getRoom("someUserId", null) }
                    assertThat(result).isNull()
                }
            }
        }
        describe(SmsMatrixAppserviceRoomService::getOrCreateRoom.name) {
            it("should get room") {
                val room = AppserviceRoom(
                        "someRoomId",
                        mutableMapOf(AppserviceUser("userId1", true) to MemberOfProperties(1))
                )
                every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
                val result = runBlocking { cut.getOrCreateRoom("someRoomId") }
                assertThat(result).isEqualTo(room)
            }
            it("should create room and fetch all members") {
                val user1 = AppserviceUser("userId1", true)
                val user2 = AppserviceUser("userId2", false)
                val roomWithoutUsers = AppserviceRoom("someRoomId")
                val roomWithUsers = AppserviceRoom(
                        "someRoomId",
                        mutableMapOf(user1 to MemberOfProperties(1), user2 to MemberOfProperties(24))
                )

                every { roomRepositoryMock.save<AppserviceRoom>(any()) }.returnsMany(
                        Mono.just(roomWithoutUsers),
                        Mono.just(roomWithUsers)
                )
                every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.empty())
                coEvery { matrixClientMock.roomsApi.getJoinedMembers("someRoomId") }
                        .returns(
                                GetJoinedMembersResponse(
                                        mapOf(
                                                "userId1" to RoomMember(),
                                                "userId2" to RoomMember()
                                        )
                                )
                        )
                coEvery { userServiceMock.getUser("userId1") }.returns(user1)
                coEvery { userServiceMock.getUser("userId2") }.returns(user2)
                coEvery { userServiceMock.getLastMappingToken("userId1") }.returns(0)
                coEvery { userServiceMock.getLastMappingToken("userId2") }.returns(23)

                val result = runBlocking { cut.getOrCreateRoom("someRoomId") }

                assertThat(result).isEqualTo(roomWithUsers)
                verify {
                    roomRepositoryMock.save<AppserviceRoom>(roomWithUsers)
                }
            }
        }
        describe(SmsMatrixAppserviceRoomService::getRoomsWithUsers.name) {
            it("should get rooms with users") {
                val rooms = arrayOf(AppserviceRoom("roomId1"), AppserviceRoom("roomId2"))
                every { roomRepositoryMock.findByMembersUserIdContaining(any()) }
                        .returns(Flux.just(*rooms))
                val result = runBlocking { cut.getRoomsWithUsers(setOf("userId1", "userId2")).toList() }
                assertThat(result).containsAll(rooms.asList())
            }
        }
        describe(SmsMatrixAppserviceRoomService::sendRoomMessage.name) {
            describe("sending time is in future") {
                it("should save message to send it later") {
                    val message = RoomMessage(mockk(), "some body", Instant.now().plusSeconds(2000))
                    every { messageRepositoryMock.save<RoomMessage>(any()) }.returns(Mono.just(message))
                    runBlocking { cut.sendRoomMessage(message) }
                    verify { messageRepositoryMock.save<RoomMessage>(message) }
                    coVerify(exactly = 0) {
                        matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any(), any())
                    }
                    verify(exactly = 0) {
                        messageRepositoryMock.delete(message)
                    }
                }
            }
            describe("sending time is in long past") {
                val room = AppserviceRoom(
                        "someRoomId", mutableMapOf(
                        AppserviceUser("someUserId1", false) to MemberOfProperties(12)
                )
                )
                it("should try to send and delete messages when sending fails") {
                    val message = RoomMessage(room, "some body", Instant.ofEpochMilli(123))

                    coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), txnId = any()) }.throws(
                            MatrixServerException(
                                    FORBIDDEN,
                                    ErrorResponse("M_FORBIDDEN")
                            )
                    )

                    runBlocking { cut.sendRoomMessage(message) }

                    coVerifyAll {
                        matrixClientMock.roomsApi.sendRoomEvent(
                                "someRoomId",
                                match<TextMessageEventContent> { it.body == "some body" },
                                txnId = any()
                        )
                    }
                    verify {
                        messageRepositoryMock.delete(message)
                    }
                }
                it("should delete messages when members are missing in room") {
                    val message = RoomMessage(
                            room,
                            "some body",
                            Instant.ofEpochMilli(123),
                            setOf("someUserId1", "someUserId2")
                    )
                    ReflectionTestUtils.setField(message, "id", 1L)

                    runBlocking { cut.sendRoomMessage(message) }

                    coVerify {
                        messageRepositoryMock.delete(message)
                    }
                }
                it("should prevent, that messages are deleted, although it was send a few minutes ago") {
                    val message = RoomMessage(
                            room,
                            "some body",
                            Instant.ofEpochMilli(123),
                            setOf("someUserId1", "someUserId2")
                    )

                    every { messageRepositoryMock.save<RoomMessage>(any()) }.returns(Mono.just(message))


                    runBlocking { cut.sendRoomMessage(message) }

                    coVerify {
                        messageRepositoryMock.save(match {
                            it.sendAfter.until(Instant.now(), ChronoUnit.SECONDS) < 300
                            && it.copy(sendAfter = Instant.ofEpochMilli(123)) == message
                        })
                    }
                }
            }
            describe("sending time is in short past") {
                it("should send and delete message") {
                    val room = AppserviceRoom(
                            "someRoomId", mutableMapOf(
                            AppserviceUser("someUserId1", false) to MemberOfProperties(12),
                            AppserviceUser("someUserId2", true) to MemberOfProperties(24)
                    )
                    )
                    val message = RoomMessage(
                            room,
                            "some body 1",
                            Instant.now().minusSeconds(300),
                            setOf("someUserId1", "someUserId2")
                    )
                    ReflectionTestUtils.setField(message, "id", 1L)

                    coEvery {
                        matrixClientMock.roomsApi.sendRoomEvent(
                                any(),
                                any(),
                                txnId = any()
                        )
                    }.returns("someId")

                    runBlocking { cut.sendRoomMessage(message) }

                    coVerifyAll {
                        matrixClientMock.roomsApi.sendRoomEvent(
                                "someRoomId",
                                match<TextMessageEventContent> { it.body == "some body 1" },
                                txnId = any()
                        )
                    }
                    verify {
                        messageRepositoryMock.delete(message)
                    }
                }
                it("should try to send but not delete message") {
                    val room = AppserviceRoom(
                            "someRoomId", mutableMapOf(
                            AppserviceUser("someUserId1", false) to MemberOfProperties(12)
                    )
                    )
                    val message = RoomMessage(room, "some body", Instant.now().minusSeconds(300))
                    ReflectionTestUtils.setField(message, "id", 1L)


                    coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), txnId = any()) }
                            .throws(RuntimeException("woops"))

                    runBlocking { cut.sendRoomMessage(message) }

                    coVerify {
                        matrixClientMock.roomsApi.sendRoomEvent(
                                "someRoomId",
                                match<TextMessageEventContent> { it.body == "some body" },
                                txnId = any()
                        )
                    }
                    verify(exactly = 0) {
                        messageRepositoryMock.delete(message)
                    }
                }
                it("should not send messages when members are missing in room") {
                    val room = AppserviceRoom(
                            "someRoomId", mutableMapOf(
                            AppserviceUser("someUserId1", false) to MemberOfProperties(12)
                    )
                    )
                    val message = RoomMessage(
                            room,
                            "some body",
                            Instant.now().minusSeconds(300),
                            setOf("someUserId1", "someUserId2")
                    )
                    ReflectionTestUtils.setField(message, "id", 1L)

                    coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), txnId = any()) }
                            .throws(RuntimeException("woops"))

                    runBlocking { cut.sendRoomMessage(message) }

                    coVerify(exactly = 0) {
                        matrixClientMock.roomsApi.sendRoomEvent(
                                "someRoomId",
                                match<TextMessageEventContent> { it.body == "some body" },
                                txnId = any()
                        )
                    }
                    verify(exactly = 0) {
                        messageRepositoryMock.delete(message)
                    }
                }
            }
        }
    }
}