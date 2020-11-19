package net.folivo.matrix.bridge.sms.provider.android

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.mockserver.MockServerListener
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.folivo.matrix.bridge.sms.SmsBridgeProperties
import net.folivo.matrix.bridge.sms.handler.ReceiveSmsService
import net.folivo.matrix.core.model.MatrixId.EventId
import net.folivo.matrix.core.model.MatrixId.RoomId
import net.folivo.matrix.core.model.events.m.room.message.NoticeMessageEventContent
import net.folivo.matrix.restclient.MatrixClient
import org.mockserver.client.MockServerClient
import org.mockserver.matchers.MatchType.STRICT
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest
import org.mockserver.model.HttpResponse
import org.mockserver.model.JsonBody
import org.mockserver.model.MediaType
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.r2dbc.core.DatabaseClient
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.core.delete
import org.springframework.data.r2dbc.core.select
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(
        properties = [
            "matrix.bridge.sms.provider.android.enabled=true",
            "matrix.bridge.sms.provider.android.baseUrl=http://localhost:5100",
            "matrix.bridge.sms.provider.android.username=username",
            "matrix.bridge.sms.provider.android.password=password"
        ]
)
class AndroidSmsProviderIT(
        dbClient: DatabaseClient,
        @MockkBean(relaxed = true)
        private val receiveSmsServiceMock: ReceiveSmsService,
        @MockkBean
        private val matrixClientMock: MatrixClient,
        @SpykBean
        private val smsBridgeProperties: SmsBridgeProperties,
        cut: AndroidSmsProvider
) : DescribeSpec(testBody(cut, dbClient, receiveSmsServiceMock, matrixClientMock, smsBridgeProperties))

private fun testBody(
        cut: AndroidSmsProvider,
        dbClient: DatabaseClient,
        receiveSmsServiceMock: ReceiveSmsService,
        matrixClientMock: MatrixClient,
        smsBridgeProperties: SmsBridgeProperties
): DescribeSpec.() -> Unit {
    return {
        val entityTemplate = R2dbcEntityTemplate(dbClient)

        listener(MockServerListener(5100))
        val mockServerClient = MockServerClient("localhost", 5100)

        beforeTest {
            mockServerClient.reset()
            coEvery { matrixClientMock.roomsApi.sendRoomEvent(any(), any(), any(), any()) }
                    .returns(EventId("event", "server"))
        }

        describe(AndroidSmsProvider::getAndProcessNewMessages.name) {
            beforeTest {
                mockServerClient
                        .`when`(
                                HttpRequest.request()
                                        .withMethod(HttpMethod.GET.name)
                                        .withPath("/messages/in"),
                                Times.exactly(1)
                        ).respond(
                                HttpResponse.response()
                                        .withBody(
                                                """
                                           {
                                                "nextBatch":"2",
                                                "messages":[
                                                    {
                                                        "number":"+4917331111111",
                                                        "body":"some body 1",
                                                        "id":1
                                                    },
                                                    {
                                                        "number":"+4917332222222",
                                                        "body":"some body 2",
                                                        "id":2
                                                    }
                                                ]
                                            }
                                          """.trimIndent(), MediaType.APPLICATION_JSON
                                        )
                        )
                mockServerClient
                        .`when`(
                                HttpRequest.request()
                                        .withMethod(HttpMethod.GET.name)
                                        .withPath("/messages/in")
                                        .withQueryStringParameter("after", "2"),
                                Times.exactly(1)
                        ).respond(
                                HttpResponse.response()
                                        .withBody(
                                                """
                                           {
                                                "nextBatch":"3",
                                                "messages":[
                                                    {
                                                        "number":"+4917333333333",
                                                        "body":"some body 3",
                                                        "id":3
                                                    }
                                                ]
                                            }
                                          """.trimIndent(), MediaType.APPLICATION_JSON
                                        )
                        )
            }
            it("should get new messages") {
                cut.getAndProcessNewMessages()
                coVerify {
                    receiveSmsServiceMock.receiveSms("some body 1", "+4917331111111")
                    receiveSmsServiceMock.receiveSms("some body 2", "+4917332222222")
                }
            }
            it("should use next batch") {
                cut.getAndProcessNewMessages()
                cut.getAndProcessNewMessages()
                mockServerClient
                        .verify(
                                HttpRequest.request()
                                        .withMethod(HttpMethod.GET.name)
                                        .withPath("/messages/in"),
                                HttpRequest.request()
                                        .withMethod(HttpMethod.GET.name)
                                        .withPath("/messages/in")
                                        .withQueryStringParameter("after", "2")
                        )
            }
            it("should save last processed message") {
                entityTemplate.delete<AndroidSmsProcessed>().all().awaitFirst()
                entityTemplate.select<AndroidSmsProcessed>().first().awaitFirstOrNull()
                        ?.shouldBeNull()
                cut.getAndProcessNewMessages()
                entityTemplate.select<AndroidSmsProcessed>().first().awaitFirstOrNull()
                        ?.lastProcessedId.shouldBe(2)
                cut.getAndProcessNewMessages()
                entityTemplate.select<AndroidSmsProcessed>().first().awaitFirstOrNull()
                        ?.lastProcessedId.shouldBe(3)
            }
            it("should handle exceptions while processing message") {
                coEvery { receiveSmsServiceMock.receiveSms(any(), "+4917332222222") }
                        .throws(RuntimeException())
                shouldThrow<RuntimeException> {
                    cut.getAndProcessNewMessages()
                }
                entityTemplate.select<AndroidSmsProcessed>().first().awaitFirstOrNull()
                        ?.lastProcessedId.shouldBe(1)
            }
        }
        describe(AndroidSmsProvider::sendSms.name) {
            it("should call android device to send message") {
                mockServerClient.`when`(
                        HttpRequest.request()
                                .withMethod(HttpMethod.POST.name)
                                .withPath("/messages/out")
                ).respond(HttpResponse.response())
                cut.sendSms("+491234567", "some body")
                mockServerClient.verify(
                        HttpRequest.request()
                                .withMethod(HttpMethod.POST.name)
                                .withPath("/messages/out")
                                .withBody(
                                        JsonBody.json(
                                                """
                                    {
                                        "recipientPhoneNumber":"+491234567",
                                        "message":"some body"
                                    }
                                """.trimIndent(), STRICT
                                        )
                                )
                )
            }
            describe("on error BadRequest") {
                it("should rethrow error") {
                    mockServerClient.`when`(
                            HttpRequest.request()
                                    .withMethod(HttpMethod.POST.name)
                                    .withPath("/messages/out")
                    ).respond(
                            HttpResponse.response()
                                    .withStatusCode(HttpStatus.BAD_REQUEST.value())
                                    .withBody("wrong telephone number")
                    )
                    try {
                        cut.sendSms("+491234567", "some body")
                        fail("did not throw error")
                    } catch (error: AndroidSmsProviderException) {
                        error.message.shouldBe("wrong telephone number")
                    }
                }
            }
            describe("on other error") {
                beforeTest {
                    mockServerClient.`when`(
                            HttpRequest.request()
                                    .withMethod(HttpMethod.POST.name)
                                    .withPath("/messages/out")
                    ).respond(
                            HttpResponse.response()
                                    .withStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                    .withBody("no network")
                    )
                }
                it("should save message to send later") {
                    shouldNotThrowAny {
                        cut.sendSms("+491234567", "some body")
                    }
                    val out = entityTemplate.select<AndroidOutSmsMessage>().all().asFlow().toList()
                    out.shouldHaveSize(1)
                    out.first().also {
                        it.body.shouldBe("some body")
                        it.receiver.shouldBe("+491234567")
                    }
                }
                describe("default room is present") {
                    val defaultRoomId = RoomId("default", "server")
                    beforeTest { every { smsBridgeProperties.defaultRoomId }.returns(defaultRoomId) }
                    it("should notify when no other failed message") {
                        every { smsBridgeProperties.templates.providerSendError }.returns("error: {error}")
                        cut.sendSms("+491234567", "some body")
                        coVerify {
                            matrixClientMock.roomsApi.sendRoomEvent(defaultRoomId, match<NoticeMessageEventContent> {
                                it.body == "error: no network"
                            }, any(), any(), any())
                        }
                    }
                    it("should not notify when other messages") {
                        entityTemplate.insert(AndroidOutSmsMessage("receiver", "body")).awaitFirstOrNull()
                        cut.sendSms("+491234567", "some body")
                        coVerify { matrixClientMock wasNot Called }
                    }
                }
                describe("default room is not present") {
                    beforeTest { every { smsBridgeProperties.defaultRoomId }.returns(null) }
                    it("should not notify") {
                        cut.sendSms("+491234567", "some body")
                        coVerify { matrixClientMock wasNot Called }
                    }
                }

            }
        }
        describe(AndroidSmsProvider::sendOutFailedMessages.name) {
            describe("there are no failed messages") {
                it("should do nothing") {
                    cut.sendOutFailedMessages()
                    coVerify { matrixClientMock wasNot Called }
                }
            }
            describe("there are failed messages") {
                beforeTest {
                    entityTemplate.insert(AndroidOutSmsMessage("+491234511", "some body 1")).awaitFirstOrNull()
                    entityTemplate.insert(AndroidOutSmsMessage("+491234522", "some body 2")).awaitFirstOrNull()
                    every { smsBridgeProperties.templates.providerResendSuccess }.returns("resend")
                }
                it("should send all messages") {
                    mockServerClient.`when`(
                            HttpRequest.request()
                                    .withMethod(HttpMethod.POST.name)
                                    .withPath("/messages/out")
                    ).respond(HttpResponse.response())
                    cut.sendOutFailedMessages()
                    mockServerClient.verify(
                            HttpRequest.request()
                                    .withMethod(HttpMethod.POST.name)
                                    .withPath("/messages/out")
                                    .withBody(
                                            JsonBody.json(
                                                    """
                                    {
                                        "recipientPhoneNumber":"+491234511",
                                        "message":"some body 1"
                                    }
                                """.trimIndent(), STRICT
                                            )
                                    ),
                            HttpRequest.request()
                                    .withMethod(HttpMethod.POST.name)
                                    .withPath("/messages/out")
                                    .withBody(
                                            JsonBody.json(
                                                    """
                                    {
                                        "recipientPhoneNumber":"+491234522",
                                        "message":"some body 2"
                                    }
                                """.trimIndent(), STRICT
                                            )
                                    )
                    )
                }
                describe("default room is given") {
                    val defaultRoomId = RoomId("default", "server")
                    beforeTest { every { smsBridgeProperties.defaultRoomId }.returns(defaultRoomId) }
                    it("should notify about all resend messages") {
                        mockServerClient.`when`(
                                HttpRequest.request()
                                        .withMethod(HttpMethod.POST.name)
                                        .withPath("/messages/out")
                        ).respond(HttpResponse.response())
                        cut.sendOutFailedMessages()
                        coVerify {
                            matrixClientMock.roomsApi.sendRoomEvent(defaultRoomId, match<NoticeMessageEventContent> {
                                it.body == "resend"
                            }, any(), any(), any())
                        }
                    }
                    it("should not notify when sending failed") {
                        mockServerClient.`when`(
                                HttpRequest.request()
                                        .withMethod(HttpMethod.POST.name)
                                        .withPath("/messages/out")
                        ).respond(
                                HttpResponse.response()
                                        .withStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                        .withBody("no network")
                        )
                        shouldThrow<AndroidSmsProviderException> {
                            cut.sendOutFailedMessages()
                        }
                        coVerify { matrixClientMock wasNot Called }
                    }
                }
                describe("default room is not given") {
                    beforeTest { every { smsBridgeProperties.defaultRoomId }.returns(null) }
                    it("should not notify") {
                        mockServerClient.`when`(
                                HttpRequest.request()
                                        .withMethod(HttpMethod.POST.name)
                                        .withPath("/messages/out")
                        ).respond(HttpResponse.response())
                        cut.sendOutFailedMessages()
                        coVerify { matrixClientMock wasNot Called }
                    }
                }
            }
        }

        afterTest {
            clearMocks(receiveSmsServiceMock, matrixClientMock, smsBridgeProperties)
            entityTemplate.delete<AndroidSmsProcessed>().all().awaitFirst()
            entityTemplate.delete<AndroidOutSmsMessage>().all().awaitFirst()
        }
    }
}