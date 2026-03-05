plugins {
    `java-library`
    `maven-publish`
}

group = "no.testframework"
version = "0.1.0"

repositories {
    mavenCentral()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("Test Framework Java Library")
                description.set("A runtime test framework library for Java")
                url.set("https://github.com/magnusag/test-framework")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
    repositories {
        mavenLocal()
    }
}

dependencies {
    compileOnly("org.springframework:spring-web:6.2.10")
    compileOnly("org.springframework:spring-context:6.2.10")
    api("org.slf4j:slf4j-api:2.0.17")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.0")
    testImplementation("org.hamcrest:hamcrest:3.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.springframework:spring-web:6.2.10")
    testImplementation("org.springframework:spring-webmvc:6.2.10")
    testImplementation("org.springframework:spring-context:6.2.10")
    testImplementation("org.springframework:spring-test:6.2.10")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")
    testImplementation("jakarta.servlet:jakarta.servlet-api:6.1.0")
    testImplementation("com.jayway.jsonpath:json-path:2.9.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.0")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.compilerArgs.add("-parameters")
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
