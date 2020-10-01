package net.folivo.matrix.bridge.sms

import org.testcontainers.containers.Neo4jContainer

class TestDbContainer : Neo4jContainer<TestDbContainer>() {
    companion object {
        private lateinit var instance: TestDbContainer

        fun start() {
            if (!Companion::instance.isInitialized) {
                instance = TestDbContainer()
                instance.dockerImageName = "neo4j:4.0"
                instance.start() // At this point we have a running instance as a Docker container

                // We set the properties below, so Spring will use these when it starts
                System.setProperty("spring.neo4j.uri", instance.boltUrl)
                System.setProperty("spring.neo4j.authentication.username", "neo4j")
                System.setProperty("spring.neo4j.authentication.password", instance.adminPassword)
            }
        }

        fun stop() {
            if (Companion::instance.isInitialized) {
                instance.stop()
            }
        }
    }
}