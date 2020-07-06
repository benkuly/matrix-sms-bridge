import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
    id("org.springframework.boot") version Versions.springBoot
    id("io.spring.dependency-management") version Versions.springDependencyManagement
    kotlin("jvm") version Versions.kotlin
    kotlin("kapt") version Versions.kotlin
    kotlin("plugin.spring") version Versions.kotlin
}

allprojects {
    apply(plugin = "kotlin")

    group = "net.folivo"
    version = "0.3.0.RELEASE"
    java.sourceCompatibility = JavaVersion.VERSION_11

    repositories {
        mavenCentral()
        mavenLocal()
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${Versions.kotlinxCoroutines}")
    implementation("com.michael-bull.kotlin-retry:kotlin-retry:${Versions.kotlinRetry}")

    api("org.neo4j.springframework.data:spring-data-neo4j-rx-spring-boot-starter:${Versions.neo4jrx}")

    implementation("net.folivo:matrix-spring-boot-bot:${Versions.matrixSDK}")

    implementation("com.github.ajalt:clikt-multiplatform:${Versions.clikt}")
    implementation("org.apache.ant:ant:${Versions.ant}") {
        exclude(group = "org.apache.ant", module = "ant-launcher")
    }
    implementation("com.googlecode.libphonenumber:libphonenumber:${Versions.libphonenumber}")

    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("com.ninja-squad:springmockk:${Versions.springMockk}")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.neo4j.springframework.data:spring-data-neo4j-rx-spring-boot-test-autoconfigure:${Versions.neo4jrx}") {
        exclude(group = "org.neo4j.test", module = "harness")
    }
    testImplementation("org.testcontainers:junit-jupiter:${Versions.testcontainers}")
    testImplementation("org.testcontainers:neo4j:${Versions.testcontainers}")

    testImplementation("com.squareup.okhttp3:mockwebserver")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "org.mockito", module = "mockito-core")
        exclude(group = "org.mockito", module = "mockito-junit-jupiter")
    }
}

// workaround for Clikt with Gradle
configurations {
    productionRuntimeClasspath {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        }
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

tasks.register<Exec>("docker-gammu") {
    group = "build"
    commandLine(
            "docker",
            "build",
            "--build-arg",
            "JAR_FILE=./build/libs/*.jar",
            "-t",
            "folivonet/matrix-sms-bridge:$version",
            "-t",
            "folivonet/matrix-sms-bridge:latest",
            "-f",
            "./src/main/docker/gammu/Dockerfile",
            "."
    )
    dependsOn("bootJar")
}