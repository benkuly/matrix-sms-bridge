package net.folivo.matrix.bridge.sms.provider.android
// TODO
//import com.ninjasquad.springmockk.MockkBean
//import io.kotest.core.spec.style.DescribeSpec
//import io.kotest.extensions.mockserver.MockServerListener
//import io.mockk.coVerify
//import net.folivo.matrix.bridge.sms.handler.ReceiveSmsService
//import net.folivo.matrix.restclient.MatrixClient
//import org.mockserver.client.MockServerClient
//import org.mockserver.matchers.MatchType.STRICT
//import org.mockserver.matchers.Times
//import org.mockserver.model.HttpRequest
//import org.mockserver.model.HttpResponse
//import org.mockserver.model.JsonBody
//import org.mockserver.model.MediaType
//import org.springframework.boot.test.context.SpringBootTest
//import org.springframework.http.HttpMethod
//
//@SpringBootTest(
//        properties = [
//            "matrix.bridge.sms.provider.android.enabled=true",
//            "matrix.bridge.sms.provider.android.basePath=http://localhost:5100",
//            "matrix.bridge.sms.provider.android.username=username",
//            "matrix.bridge.sms.provider.android.password=password"
//        ]
//)
//class AndroidSmsProviderIT(
//        @MockkBean(relaxed = true)
//        private val matrixClientMock: MatrixClient,
//        @MockkBean(relaxed = true)
//        private val androidSmsProviderLauncherMock: AndroidSmsProviderLauncher,
//        @MockkBean(relaxed = true)
//        private val receiveSmsServiceMock: ReceiveSmsService,
//        cut: AndroidSmsProvider
//) : DescribeSpec(testBody(cut, receiveSmsServiceMock))
//
//private fun testBody(cut: AndroidSmsProvider, receiveSmsServiceMock: ReceiveSmsService): DescribeSpec.() -> Unit {
//    return {
//        listener(MockServerListener(5100))
//
//        describe(AndroidSmsProvider::getNewMessages.name) {
//            val mockServerClient = MockServerClient("localhost", 5100)
//            mockServerClient.reset()
//            mockServerClient
//                    .`when`(
//                            HttpRequest.request()
//                                    .withMethod(HttpMethod.GET.name)
//                                    .withPath("/messages"),
//                            Times.exactly(1)
//                    ).respond(
//                            HttpResponse.response()
//                                    .withBody(
//                                            """
//                                           {
//                                                "nextBatch":"2",
//                                                "messages":[
//                                                    {
//                                                        "sender":"+4917331111111",
//                                                        "body":"some body 1"
//                                                    },
//                                                    {
//                                                        "sender":"+4917332222222",
//                                                        "body":"some body 2"
//                                                    }
//                                                ]
//                                            }
//                                          """.trimIndent(), MediaType.APPLICATION_JSON
//                                    )
//                    )
//            mockServerClient
//                    .`when`(
//                            HttpRequest.request()
//                                    .withMethod(HttpMethod.GET.name)
//                                    .withPath("/messages")
//                                    .withQueryStringParameter("nextBatch", "2"),
//                            Times.exactly(1)
//                    ).respond(
//                            HttpResponse.response()
//                                    .withBody(
//                                            """
//                                           {
//                                                "nextBatch":"3",
//                                                "messages":[]
//                                            }
//                                          """.trimIndent(), MediaType.APPLICATION_JSON
//                                    )
//                    )
//            it("should get new messages") {
//                cut.getNewMessages()
//                coVerify {
//                    receiveSmsServiceMock.receiveSms("some body 1", "+4917331111111")
//                    receiveSmsServiceMock.receiveSms("some body 2", "+4917332222222")
//                }
//            }
//            it("should save and use next batch") {
//                cut.getNewMessages()
//                mockServerClient
//                        .verify(
//                                HttpRequest.request()
//                                        .withMethod(HttpMethod.GET.name)
//                                        .withPath("/messages"),
//                                HttpRequest.request()
//                                        .withMethod(HttpMethod.GET.name)
//                                        .withPath("/messages")
//                                        .withQueryStringParameter("nextBatch", "2")
//                        )
//            }
//        }
//        describe(AndroidSmsProvider::sendSms.name) {
//            val mockServerClient = MockServerClient("localhost", 5100)
//            mockServerClient.reset()
//            it("should call android device to send message") {
//                mockServerClient.`when`(
//                        HttpRequest.request()
//                                .withMethod(HttpMethod.POST.name)
//                                .withPath("/messages")
//                ).respond(HttpResponse.response())
//                cut.sendSms("+491234567", "some body")
//                mockServerClient.verify(
//                        HttpRequest.request()
//                                .withMethod(HttpMethod.POST.name)
//                                .withPath("/messages")
//                                .withBody(
//                                        JsonBody.json(
//                                                """
//                                    {
//                                        "sender":"+491234567",
//                                        "body":"some body"
//                                    }
//                                """.trimIndent(), STRICT
//                                        )
//                                )
//                )
//            }
//        }
//    }
//}