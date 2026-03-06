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
                name.set("Test Framework Plugins")
                description.set("Ready-made runtime test suite plugins for the test framework")
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
    // Core library — annotations, model, runtime engine
    api(project(":java-library"))

    // JSON serialisation for report writing
    api("com.fasterxml.jackson.core:jackson-databind:2.18.6")

    // Spring Boot auto-configuration support (compileOnly — consumers without Spring
    // don't pull these in transitively)
    compileOnly("org.springframework.boot:spring-boot:3.4.2")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.4.2")
    compileOnly("org.springframework:spring-context:7.0.5")

    api("org.slf4j:slf4j-api:2.0.17")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:3.4.2")
    testImplementation("org.springframework:spring-context:7.0.5")
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
