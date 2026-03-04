plugins {
    `java-library`
}

group = "no.testframework"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.springframework:spring-web:6.2.8")
    compileOnly("org.springframework:spring-context:6.2.8")

    api("org.slf4j:slf4j-api:2.0.16")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.hamcrest:hamcrest:2.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
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

tasks.register<JavaExec>("runSuite") {
    group = "verification"
    description = "Runs the RuntimeTestSuiteRunnerMain to execute runtime test suites"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("no.testframework.javalibrary.runtime.RuntimeTestSuiteRunnerMain")
}

