import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
    id("org.springframework.boot") version "2.3.0.RELEASE"
    id("io.spring.dependency-management") version "1.0.9.RELEASE"
    kotlin("jvm") version "1.3.72"
    kotlin("kapt") version "1.3.72"
    kotlin("plugin.spring") version "1.3.72"
}

allprojects {
    apply(plugin = "kotlin")

    group = "net.folivo"
    version = "0.2.2.RELEASE"
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

    api("org.neo4j.springframework.data:spring-data-neo4j-rx-spring-boot-starter:1.1.0")

    implementation("net.folivo:matrix-spring-boot-bot:0.2.8.RELEASE")

    implementation("com.github.ajalt:clikt-multiplatform:2.7.1")
    implementation("org.apache.ant:ant:1.10.8") {
        exclude(group = "org.apache.ant", module = "ant-launcher")
    }
    implementation("com.googlecode.libphonenumber:libphonenumber:8.12.5")

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