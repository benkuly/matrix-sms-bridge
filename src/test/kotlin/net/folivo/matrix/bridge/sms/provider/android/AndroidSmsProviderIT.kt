package net.folivo.matrix.bridge.sms.provider.android

import com.ninjasquad.springmockk.MockkBean
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.mockserver.MockServerListener
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.folivo.matrix.bridge.sms.handler.ReceiveSmsService
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

@SpringBootTest(
        properties = [
            "matrix.bridge.sms.provider.android.enabled=true",
            "matrix.bridge.sms.provider.android.basePath=http://localhost:5100",
            "matrix.bridge.sms.provider.android.username=username",
            "matrix.bridge.sms.provider.android.password=password"
        ]
)
class AndroidSmsProviderIT(
        dbClient: DatabaseClient,
        @MockkBean(relaxed = true)
        private val receiveSmsServiceMock: ReceiveSmsService,
        cut: AndroidSmsProvider
) : DescribeSpec(testBody(cut, dbClient, receiveSmsServiceMock))

private fun testBody(
        cut: AndroidSmsProvider,
        dbClient: DatabaseClient,
        receiveSmsServiceMock: ReceiveSmsService
): DescribeSpec.() -> Unit {
    return {
        val entityTemplate = R2dbcEntityTemplate(dbClient)

        listener(MockServerListener(5100))
        val mockServerClient = MockServerClient("localhost", 5100)

        beforeTest { mockServerClient.reset() }

        describe(AndroidSmsProvider::getNewMessages.name) {
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
                cut.getNewMessages()
                coVerify {
                    receiveSmsServiceMock.receiveSms("some body 1", "+4917331111111")
                    receiveSmsServiceMock.receiveSms("some body 2", "+4917332222222")
                }
            }
            it("should use next batch") {
                cut.getNewMessages()
                cut.getNewMessages()
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
                cut.getNewMessages()
                entityTemplate.select<AndroidSmsProcessed>().first().awaitFirstOrNull()
                        ?.lastProcessedId.shouldBe(2)
                cut.getNewMessages()
                entityTemplate.select<AndroidSmsProcessed>().first().awaitFirstOrNull()
                        ?.lastProcessedId.shouldBe(3)
            }
            it("should handle exceptions while processing message") {
                coEvery { receiveSmsServiceMock.receiveSms(any(), "+4917332222222") }
                        .throws(RuntimeException())
                shouldThrow<RuntimeException> {
                    cut.getNewMessages()
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
        }

        afterTest {
            clearMocks(receiveSmsServiceMock)
        }
        afterSpec {
            entityTemplate.delete<AndroidSmsProcessed>().all().awaitFirst()
        }
    }
}