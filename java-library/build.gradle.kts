plugins {
    `java-library`
}

group = "no.testframework"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.springframework:spring-web:6.1.14")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}
