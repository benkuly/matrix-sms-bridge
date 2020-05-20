import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
    id("org.springframework.boot") version "2.2.7.RELEASE"
    id("io.spring.dependency-management") version "1.0.9.RELEASE"
    kotlin("jvm") version "1.3.72"
    kotlin("kapt") version "1.3.72"
    kotlin("plugin.spring") version "1.3.72"
}

allprojects {
    apply(plugin = "kotlin")

    group = "net.folivo"
    version = "0.1.0.RELEASE"
    java.sourceCompatibility = JavaVersion.VERSION_11

    repositories {
        mavenCentral()
    }

}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("net.folivo:matrix-spring-boot-bot:0.1.5.RELEASE")

    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("com.ninja-squad:springmockk:2.0.1")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.neo4j.springframework.data:spring-data-neo4j-rx-spring-boot-test-autoconfigure:1.0.1") {
        exclude(group = "org.neo4j.test", module = "harness")
    }
    testImplementation("org.testcontainers:junit-jupiter:1.14.1")
    testImplementation("org.testcontainers:neo4j:1.14.1")

    testImplementation("com.squareup.okhttp3:mockwebserver")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "org.mockito", module = "mockito-core")
        exclude(group = "org.mockito", module = "mockito-junit-jupiter")
    }
}

the<DependencyManagementExtension>().apply {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks {
    register<Exec>("createTestInfra") {
        workingDir = File("src/main/podman/")
        group = "infrastructure"
        commandLine("podman", "play", "kube", "testInfra.yaml")
    }
    register<Exec>("restartTestInfra") {
        workingDir = File("src/main/podman/")
        group = "infrastructure"
        doFirst {
            exec {
                isIgnoreExitValue = true
                commandLine("podman", "pod", "stop", "matrix-spring-boot-bot-examples")
            }
            exec {
                isIgnoreExitValue = true
                commandLine("podman", "pod", "rm", "matrix-spring-boot-bot-examples", "-f")
            }
        }
        commandLine("podman", "play", "kube", "testInfra.yaml")
    }
    register<Exec>("startTestInfra") {
        group = "infrastructure"
        commandLine("podman", "pod", "start", "matrix-spring-boot-bot-examples")
    }
    register<Exec>("stopTestInfra") {
        group = "infrastructure"
        commandLine("podman", "pod", "stop", "matrix-spring-boot-bot-examples")
    }
}

tasks.register<Exec>("docker-gammu") {
    group = "build"
    commandLine(
            "docker",
            "build",
            "--build-arg",
            "JAR_FILE=./build/libs/*.jar",
            "-t",
            "net.folivo/matrix-sms-bridge:$version",
            "-t",
            "net.folivo/matrix-sms-bridge:latest",
            "-f",
            "./src/main/docker/gammu/Dockerfile",
            "."
    )
    dependsOn("bootJar")
}