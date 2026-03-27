plugins {
    java
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
}

val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
val springBootVersion = providers.gradleProperty("springBootVersion").get()
val springDependencyManagementVersion = providers.gradleProperty("springDependencyManagementVersion").get()
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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
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

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
}

tasks.test {
    useJUnitPlatform()
}
