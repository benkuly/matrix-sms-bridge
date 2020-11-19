import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
    id("org.springframework.boot") version Versions.springBoot
    id("io.spring.dependency-management") version Versions.springDependencyManagement
    kotlin("jvm") version Versions.kotlin
    kotlin("kapt") version Versions.kotlin
    kotlin("plugin.spring") version Versions.kotlin
}

repositories {
    maven("https://repo.spring.io/milestone")
}

allprojects {
    apply(plugin = "kotlin")

    group = "net.folivo"
    version = "0.5.0"
    java.sourceCompatibility = JavaVersion.VERSION_11

    repositories {
        mavenCentral()
        mavenLocal()
        maven("https://repo.spring.io/milestone")
    }
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("com.michael-bull.kotlin-retry:kotlin-retry:${Versions.kotlinRetry}")

    implementation("net.folivo:matrix-spring-boot-bot:${Versions.matrixSDK}")

    implementation("com.github.ajalt.clikt:clikt:${Versions.clikt}")
    implementation("org.apache.ant:ant:${Versions.ant}") {
        exclude(group = "org.apache.ant", module = "ant-launcher")
    }
    implementation("com.googlecode.libphonenumber:libphonenumber:${Versions.libphonenumber}")

    implementation("io.r2dbc:r2dbc-h2")
    implementation("com.h2database:h2")

    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("io.kotest:kotest-runner-junit5:${Versions.kotest}")
    testImplementation("io.kotest:kotest-assertions-core:${Versions.kotest}")
    testImplementation("io.kotest:kotest-property:${Versions.kotest}")
    testImplementation("io.kotest:kotest-extensions-spring:${Versions.kotest}")
    testImplementation("io.kotest:kotest-extensions-mockserver:${Versions.kotest}")
    testImplementation("com.ninja-squad:springmockk:${Versions.springMockk}")
    testImplementation("io.projectreactor:reactor-test")

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

dependencyManagement {
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