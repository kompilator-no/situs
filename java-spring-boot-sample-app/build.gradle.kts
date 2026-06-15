plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
val assertjVersion = providers.gradleProperty("assertjVersion").get()

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
