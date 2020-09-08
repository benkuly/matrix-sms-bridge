package net.folivo.matrix.bridge.sms.provider.android

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.mockserver.MockServerListener
import io.kotest.extensions.testcontainers.perSpec
import org.mockserver.client.MockServerClient
import org.mockserver.matchers.MatchType.STRICT
import org.mockserver.model.HttpRequest
import org.mockserver.model.JsonBody.json
import org.neo4j.driver.springframework.boot.test.autoconfigure.Neo4jTestHarnessAutoConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.Neo4jContainer

@SpringBootTest
@EnableAutoConfiguration(exclude = [Neo4jTestHarnessAutoConfiguration::class])
class AndroidSmsProviderIT(private val cut: AndroidSmsProvider) : DescribeSpec() {

    companion object {
        private val neo4jContainer: Neo4jContainer<*> = Neo4jContainer<Nothing>("neo4j:4.0")

        @DynamicPropertySource
        @JvmStatic
        fun testProperties(registry: DynamicPropertyRegistry) {
            registry.add("org.neo4j.driver.uri", neo4jContainer::getBoltUrl)
            registry.add("org.neo4j.driver.authentication.username") { "neo4j" }
            registry.add("org.neo4j.driver.authentication.password", neo4jContainer::getAdminPassword)

            registry.add("matrix.bridge.sms.provider.android.enabled") { true }
            registry.add("matrix.bridge.sms.provider.android.basePath") { "http://localhost:5100" }
            registry.add("matrix.bridge.sms.provider.android.username") { "username" }
            registry.add("matrix.bridge.sms.provider.android.password") { "password" }

        }
    }

    init {

        listener(neo4jContainer.perSpec())
        listener(MockServerListener(5100))

        describe(AndroidSmsProvider::sendSms.name) {
            it("should call android device") {
                MockServerClient("localhost", 5100).verify(
                        HttpRequest.request()
                                .withMethod(HttpMethod.POST.name)
                                .withPath("/messages")
                                .withBody(
                                        json(
                                                """
                                    {
                                        "sender":"+491234567",
                                        "body":"some body"
                                    }
                                """.trimIndent(), STRICT
                                        )
                                )
                )
                cut.sendSms("+491234567", "some body")
            }
        }
    }
}