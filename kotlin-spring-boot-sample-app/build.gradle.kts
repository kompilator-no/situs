plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

import org.springframework.boot.gradle.tasks.bundling.BootJar

val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
val assertjVersion = providers.gradleProperty("assertjVersion").get()

kotlin {
    jvmToolchain(javaVersion)
}

dependencies {
    implementation(project(":situs"))
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
