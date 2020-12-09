package net.folivo.matrix.bridge.sms.mapping

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.describeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import net.folivo.matrix.bot.membership.MatrixMembership
import net.folivo.matrix.bot.membership.MatrixMembershipService
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.core.model.MatrixId.RoomId
import net.folivo.matrix.core.model.MatrixId.UserId

class MatrixSmsMappingServiceTest : DescribeSpec(testBody())

private fun testBody(): DescribeSpec.() -> Unit {
    return {
        val mappingRepositoryMock: MatrixSmsMappingRepository = mockk()
        val membershipServiceMock: MatrixMembershipService = mockk()
        val smsBridgePropertiesMock: SmsBridgeProperties = mockk()

        val cut = MatrixSmsMappingService(mappingRepositoryMock, membershipServiceMock, smsBridgePropertiesMock)

        val userId = UserId("user", "server")
        val roomId = RoomId("room", "server")
        val membership = MatrixMembership(userId, roomId)
        val mapping = MatrixSmsMapping(membership.id, 2)

        describe(MatrixSmsMappingService::getOrCreateMapping.name) {
            beforeTest {
                coEvery { membershipServiceMock.getOrCreateMembership(userId, roomId) }
                    .returns(membership)
            }
            describe("mapping in database") {
                beforeTest { coEvery { mappingRepositoryMock.findByMembershipId(membership.id) }.returns(mapping) }
                it("should return entity from database") {
                    cut.getOrCreateMapping(userId, roomId).shouldBe(mapping)
                    coVerify(exactly = 0) { mappingRepositoryMock.save(any()) }
                }
            }
            describe("mapping not in database") {
                beforeTest { coEvery { mappingRepositoryMock.findByMembershipId(membership.id) }.returns(null) }
                describe("no last mapping token found") {
                    beforeTest {
                        coEvery { mappingRepositoryMock.findByUserIdSortByMappingTokenDesc(userId) }
                            .returns(flowOf())
                    }
                    it("should create and save new entity and start with 1 as token") {
                        val savedMapping = MatrixSmsMapping(membership.id, 1, 2)
                        coEvery { mappingRepositoryMock.save(MatrixSmsMapping(membership.id, 1)) }
                            .returns(savedMapping)
                        cut.getOrCreateMapping(userId, roomId).shouldBe(savedMapping)
                    }
                }
                describe("last mapping token found") {
                    beforeTest {
                        coEvery { mappingRepositoryMock.findByUserIdSortByMappingTokenDesc(userId) }
                            .returns(flowOf(MatrixSmsMapping(membership.id, 14, 2)))
                    }
                    it("should create and save new entity and start with 1 as token") {
                        val savedMapping = MatrixSmsMapping(membership.id, 15, 2)
                        coEvery { mappingRepositoryMock.save(MatrixSmsMapping(membership.id, 15)) }
                            .returns(savedMapping)
                        cut.getOrCreateMapping(userId, roomId).shouldBe(savedMapping)
                    }
                }
            }
        }
        describe(MatrixSmsMappingService::getRoomId.name) {
            val testFindSingleRoomIdMapping = { name: String ->
                describeSpec {
                    describe("$name allow mapping without token") {
                        beforeTest { every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(true) }
                        describe("user is in no room") {
                            beforeTest { coEvery { membershipServiceMock.getMembershipsByUserId(userId) }.returns(flowOf()) }
                            it("should return null") {
                                cut.getRoomId(userId, null).shouldBeNull()
                            }
                        }
                        describe("user is in one room") {
                            beforeTest {
                                coEvery { membershipServiceMock.getMembershipsByUserId(userId) }
                                    .returns(flowOf(membership))
                            }
                            it("should return room id") {
                                cut.getRoomId(userId, null).shouldBe(membership.roomId)
                            }
                        }
                        describe("user is in more then one room") {
                            beforeTest {
                                coEvery { membershipServiceMock.getMembershipsByUserId(userId) }
                                    .returns(flowOf(mockk(), mockk()))
                            }
                            it("should return null") {
                                cut.getRoomId(userId, null).shouldBeNull()
                            }
                        }
                    }
                    describe("$name not allow mapping without token") {
                        beforeTest { every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(false) }
                        it("should return null") {
                            cut.getRoomId(userId, null).shouldBeNull()
                        }
                    }
                }
            }
            describe("mapping token present") {
                beforeTest { every { smsBridgePropertiesMock.allowMappingWithoutToken }.returns(true) }
                describe("mapping token in database") {
                    beforeTest {
                        coEvery { mappingRepositoryMock.findByUserIdAndMappingToken(userId, 2) }
                            .returns(mapping)
                        coEvery { membershipServiceMock.getMembership(mapping.membershipId) }
                            .returns(membership)
                    }
                    it("should return room id from database") {
                        cut.getRoomId(userId, 2).shouldBe(membership.roomId)
                    }
                }
                describe("mapping token not in database") {
                    beforeTest {
                        coEvery { mappingRepositoryMock.findByUserIdAndMappingToken(userId, 2) }
                            .returns(null)
                    }
                    include(testFindSingleRoomIdMapping("a"))
                }
            }
            describe("mapping token not present") {
                include(testFindSingleRoomIdMapping("b"))
            }
        }

        afterTest { clearMocks(mappingRepositoryMock, membershipServiceMock, smsBridgePropertiesMock) }
    }
}