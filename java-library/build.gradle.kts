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
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.18.6")
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

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        // Check HTML structure, broken @links and syntax — but not missing docs on private members
        addStringOption("Xdoclint:html,reference,syntax", "-quiet")
        // Treat all doclint warnings as errors so the build fails on any issue
        addBooleanOption("Werror", true)
    }
}

tasks.register<Delete>("cleanupOldApi") {
    group = "build"
    description = "Deletes the deprecated api/ package and duplicate spring/model/ classes"
    val base = "src/main/java/no/testframework/javalibrary"
    delete("$base/api")
    delete("$base/spring/model/TestCase.java")
    delete("$base/spring/model/TestCaseResult.java")
    delete("$base/spring/model/TestSuite.java")
    delete("$base/spring/model/TestSuiteResult.java")
}

tasks.register<JavaExec>("runSuite") {
    group = "verification"
    description = "Runs the RuntimeTestSuiteRunnerMain to execute runtime test suites"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("no.testframework.javalibrary.runtime.RuntimeTestSuiteRunnerMain")
}
