package net.folivo.matrix.bridge.sms.room

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import net.folivo.matrix.appservice.api.room.MatrixAppserviceRoomService.RoomExistingState.DOES_NOT_EXISTS
import net.folivo.matrix.bot.config.MatrixBotProperties
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.appservice.SmsMatrixAppserviceUserService
import net.folivo.matrix.bridge.sms.mapping.MatrixSmsMapping
import net.folivo.matrix.bridge.sms.message.MatrixMessage
import net.folivo.matrix.bridge.sms.message.MatrixMessageRepository
import net.folivo.matrix.bridge.sms.user.AppserviceUser
import net.folivo.matrix.core.api.ErrorResponse
import net.folivo.matrix.core.api.MatrixServerException
import net.folivo.matrix.core.model.events.m.room.message.TextMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import net.folivo.matrix.restclient.api.rooms.GetJoinedMembersResponse
import net.folivo.matrix.restclient.api.rooms.GetJoinedMembersResponse.RoomMember
import org.assertj.core.api.Assertions
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.test.util.ReflectionTestUtils
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.temporal.ChronoUnit.SECONDS

class SmsMatrixAppserviceRoomServiceTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val roomRepositoryMock: AppserviceRoomRepository = mockk()
        val userServiceMock: SmsMatrixAppserviceUserService = mockk()
        val messageRepositoryMock: MatrixMessageRepository = mockk()
        val botPropertiesMock: MatrixBotProperties = mockk()
        val smsBridgePropertiesMock: SmsBridgeProperties = mockk()
        val matrixClientMock: MatrixClient = mockk(relaxed = true)

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
                    cut,
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
                Assertions.assertThat(result).isEqualTo(DOES_NOT_EXISTS)
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
                val user = AppserviceUser("someUserId", true)
                val room = AppserviceRoom("someRoomId", listOf(MatrixSmsMapping(mockk(), 1)))

                every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
                coEvery { userServiceMock.getOrCreateUser("someUserId") }.returns(user)
                coEvery { userServiceMock.getLastMappingToken("someUserId") }.returns(23)
                every { roomRepositoryMock.save<AppserviceRoom>(any()) }.returns(Mono.just(room))

                runBlocking {
                    cut.saveRoomJoin("someRoomId", "someUserId")
                }

                verify {
                    roomRepositoryMock.save<AppserviceRoom>(match {
                        it.memberships.contains(MatrixSmsMapping(user, 24))
                    })
                }
            }
            describe("if room does not exists yet") {
                it("should save user join to room") {
                    val user = AppserviceUser("someUserId", true)
                    val existingUser = AppserviceUser("someExistingUserId", false)
                    val roomWithoutMember = AppserviceRoom("someRoomId")
                    val roomWithMember = AppserviceRoom(
                            "someRoomId", listOf(
                            MatrixSmsMapping(existingUser, 1),
                            MatrixSmsMapping(user, 1)
                    )
                    )
                    coEvery { userServiceMock.getOrCreateUser("someExistingUserId") }.returns(existingUser)
                    coEvery { userServiceMock.getOrCreateUser("someUserId") }.returns(user)
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

        describe(SmsMatrixAppserviceRoomService::getRoom.name) {
            val room = AppserviceRoom("someRoomId2")
            describe("matching token given") {
                describe("matching room found") {
                    it("should get room") {
                        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)
                        coEvery { roomRepositoryMock.findByUserIdAndMappingToken("someUserId", 24) }
                                .returns(Mono.just(room))
                        val result = runBlocking { cut.getRoom("someUserId", 24) }
                        result shouldBe room
                    }
                }
                describe("matching room not found") {
                    coEvery { roomRepositoryMock.findByUserIdAndMappingToken("someUserId", 24) }
                            .returns(Mono.empty())
                    it("should get room when allowMappingWithoutToken and one room found") {
                        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(true)
                        coEvery { roomRepositoryMock.findByUserIdAndMappingToken("someUserId", 24) }
                                .returns(Mono.empty())
                        coEvery { roomRepositoryMock.findAllByUserId("someUserId") }
                                .returns(Flux.just(room))
                        val result = runBlocking { cut.getRoom("someUserId", 24) }
                        result shouldBe room
                    }
                    it("should not get room when allowMappingWithoutToken and two room found") {
                        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(true)
                        coEvery { roomRepositoryMock.findByUserIdAndMappingToken("someUserId", 24) }
                                .returns(Mono.empty())
                        coEvery { roomRepositoryMock.findAllByUserId("someUserId") }
                                .returns(Flux.just(room, AppserviceRoom("someOtherRoom")))
                        val result = runBlocking { cut.getRoom("someUserId", 24) }
                        result shouldBe null
                    }
                    it("should not get room when not allowMappingWithoutToken") {
                        every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)
                        coEvery { roomRepositoryMock.findByUserIdAndMappingToken("someUserId", 24) }
                                .returns(Mono.empty())
                        val result = runBlocking { cut.getRoom("someUserId", 24) }
                        result shouldBe null
                    }
                }
            }
            describe("no matching token given") {
                it("should get first and only room when allowMappingWithoutToken") {
                    every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(true)
                    coEvery { roomRepositoryMock.findAllByUserId("someUserId") }
                            .returns(Flux.just(room))
                    val result = runBlocking { cut.getRoom("someUserId", null) }
                    result shouldBe room
                }
                it("should not get room when not allowMappingWithoutToken") {
                    every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false)
                    val result = runBlocking { cut.getRoom("someUserId", null) }
                    result shouldBe null
                }
                it("should not get room when two rooms") {
                    every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(true)

                    coEvery { roomRepositoryMock.findAllByUserId("someUserId") }
                            .returns(Flux.just(room, AppserviceRoom("someOtherRoomId")))
                    val result = runBlocking { cut.getRoom("someUserId", null) }
                    result shouldBe null
                }
            }
        }
        describe(SmsMatrixAppserviceRoomService::getOrCreateRoom.name) {
            it("should get room") {
                val room = AppserviceRoom("someRoomId", listOf(MatrixSmsMapping(AppserviceUser("userId1", true), 1)))
                every { roomRepositoryMock.findById("someRoomId") }.returns(Mono.just(room))
                val result = runBlocking { cut.getOrCreateRoom("someRoomId") }
                Assertions.assertThat(result).isEqualTo(room)
            }
            it("should create room and fetch all members") {
                val user1 = AppserviceUser("userId1", true)
                val user2 = AppserviceUser("userId2", false)
                val roomWithoutUsers = AppserviceRoom("someRoomId")
                val roomWithUsers = AppserviceRoom(
                        "someRoomId",
                        listOf(MatrixSmsMapping(user1, 1), MatrixSmsMapping(user2, 24))
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
                coEvery { userServiceMock.getOrCreateUser("userId1") }.returns(user1)
                coEvery { userServiceMock.getOrCreateUser("userId2") }.returns(user2)
                coEvery { userServiceMock.getLastMappingToken("userId1") }.returns(0)
                coEvery { userServiceMock.getLastMappingToken("userId2") }.returns(23)

                val result = runBlocking { cut.getOrCreateRoom("someRoomId") }

                Assertions.assertThat(result).isEqualTo(roomWithUsers)
                verify {
                    roomRepositoryMock.save<AppserviceRoom>(roomWithUsers)
                }
            }
        }
        describe(SmsMatrixAppserviceRoomService::getRoomsWithMembers.name) {
            it("should get rooms with users") {
                val rooms = arrayOf(AppserviceRoom("roomId1"), AppserviceRoom("roomId2"))
                every { roomRepositoryMock.findByMembersUserIdContaining(any()) }
                        .returns(Flux.just(*rooms))
                val result = runBlocking { cut.getRoomsWithMembers(setOf("userId1", "userId2")).toList() }
                Assertions.assertThat(result).containsAll(rooms.asList())
            }
        }
        describe(SmsMatrixAppserviceRoomService::sendRoomMessage.name) {
            describe("sending time is in future") {
                it("should save message to send it later") {
                    val message = MatrixMessage(mockk(), "some body", Instant.now().plusSeconds(2000))
                    every { messageRepositoryMock.save<MatrixMessage>(any()) }.returns(Mono.just(message))
                    runBlocking { cut.sendRoomMessage(message) }
                    verify { messageRepositoryMock.save<MatrixMessage>(message) }
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
                        "someRoomId", listOf(
                        MatrixSmsMapping(AppserviceUser("someUserId1", false), 12)
                )
                )
                it("should try to send and delete messages when sending fails") {
                    val message = MatrixMessage(room, "some body", Instant.ofEpochMilli(123))

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
                    val message = MatrixMessage(
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
                    val message = MatrixMessage(
                            room,
                            "some body",
                            Instant.ofEpochMilli(123),
                            setOf("someUserId1", "someUserId2")
                    )

                    every { messageRepositoryMock.save<MatrixMessage>(any()) }.returns(Mono.just(message))


                    runBlocking { cut.sendRoomMessage(message) }

                    coVerify {
                        messageRepositoryMock.save(match {
                            it.sendAfter.until(Instant.now(), SECONDS) < 300
                            && it.copy(sendAfter = Instant.ofEpochMilli(123)) == message
                        })
                    }
                }
            }
            describe("sending time is in short past") {
                it("should send and delete message") {
                    val room = AppserviceRoom(
                            "someRoomId", listOf(
                            MatrixSmsMapping(AppserviceUser("someUserId1", false), 12),
                            MatrixSmsMapping(AppserviceUser("someUserId2", true), 24)
                    )
                    )
                    val message = MatrixMessage(
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
                            "someRoomId", listOf(MatrixSmsMapping(AppserviceUser("someUserId1", false), 12))
                    )
                    val message = MatrixMessage(room, "some body", Instant.now().minusSeconds(300))
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
                            "someRoomId", listOf(
                            MatrixSmsMapping(AppserviceUser("someUserId1", false), 12)
                    )
                    )
                    val message = MatrixMessage(
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
        describe(SmsMatrixAppserviceRoomService::syncUserAndItsRooms.name) {
            it("should fetch rooms of user and users of room") {
                every { roomRepositoryMock.findAllByUserId("someUserId1") }
                        .returns(Flux.empty())
                coEvery { matrixClientMock.roomsApi.getJoinedRooms(asUserId = "someUserId1") }
                        .returns(flowOf("someRoomId1", "someRoomId2"))
                coEvery { matrixClientMock.roomsApi.getJoinedMembers("someRoomId1", asUserId = "someUserId1") }
                        .returns(
                                GetJoinedMembersResponse(
                                        joined = mapOf(
                                                "someUserId1" to RoomMember(),
                                                "someUserId2" to RoomMember()
                                        )
                                )
                        )
                coEvery { matrixClientMock.roomsApi.getJoinedMembers("someRoomId2", asUserId = "someUserId1") }
                        .returns(
                                GetJoinedMembersResponse(joined = mapOf("someUserId1" to RoomMember()))
                        )
                coEvery { cut.saveRoomJoin(any(), any()) } just Runs

                cut.syncUserAndItsRooms("someUserId1")

                coVerify {
                    cut.saveRoomJoin("someRoomId1", "someUserId1")
                    cut.saveRoomJoin("someRoomId1", "someUserId2")
                    cut.saveRoomJoin("someRoomId2", "someUserId1")
                }
            }
            it("should fetch rooms of user and users of room as bot user") {
                every { roomRepositoryMock.findAllByUserId("@bot:someServer") }
                        .returns(Flux.empty())
                coEvery { matrixClientMock.roomsApi.getJoinedRooms(asUserId = null) }
                        .returns(flowOf("someRoomId1", "someRoomId2"))
                coEvery { matrixClientMock.roomsApi.getJoinedMembers("someRoomId1", asUserId = null) }
                        .returnsMany(
                                GetJoinedMembersResponse(
                                        joined = mapOf(
                                                "someUserId1" to RoomMember(),
                                                "someUserId2" to RoomMember()
                                        )
                                )
                        )
                coEvery { cut.saveRoomJoin(any(), any()) } just Runs

                cut.syncUserAndItsRooms()

                coVerify {
                    cut.saveRoomJoin("someRoomId1", "someUserId1")
                    cut.saveRoomJoin("someRoomId1", "someUserId2")
                }
            }
            it("should not fetch rooms when user and rooms does exists") {
                every { roomRepositoryMock.findAllByUserId("someUserId1") }
                        .returns(Flux.just(AppserviceRoom("someRoomId")))

                cut.syncUserAndItsRooms("someUserId1")

                coVerify {
                    matrixClientMock wasNot Called
                }
                coVerify(exactly = 0) {
                    cut.saveRoomJoin(any(), any())
                }
            }
        }
    }
}
