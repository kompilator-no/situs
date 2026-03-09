plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.spring") version "2.3.10"
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
}

import org.springframework.boot.gradle.tasks.bundling.BootJar

val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
val springBootVersion = providers.gradleProperty("springBootVersion").get()
val springDependencyManagementVersion = providers.gradleProperty("springDependencyManagementVersion").get()
val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
val assertjVersion = providers.gradleProperty("assertjVersion").get()

plugins.withId("org.springframework.boot") {
    check(springBootVersion == "4.0.3") {
        "The org.springframework.boot plugin version must stay aligned with gradle.properties springBootVersion"
    }
}

plugins.withId("io.spring.dependency-management") {
    check(springDependencyManagementVersion == "1.1.7") {
        "The io.spring.dependency-management plugin version must stay aligned with gradle.properties springDependencyManagementVersion"
    }
}

plugins.withId("org.jetbrains.kotlin.jvm") {
    check(kotlinVersion == "2.3.10") {
        "The Kotlin plugin version must stay aligned with gradle.properties kotlinVersion"
    }
}

kotlin {
    jvmToolchain(javaVersion)
}

dependencies {
    implementation(project(":java-library"))
    implementation(project(":plugins"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<BootJar>().configureEach {
    mainClass.set("no.kompilator.kotlinapp.KotlinSampleApplicationKt")
}
