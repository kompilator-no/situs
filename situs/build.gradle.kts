import org.gradle.api.publish.maven.MavenPublication
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.plugins.signing.Sign

plugins {
    `java-library`
    `maven-publish`
    signing
}

val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
val springVersion = providers.gradleProperty("springVersion").get()
val springBootVersion = providers.gradleProperty("springBootVersion").get()
val slf4jVersion = providers.gradleProperty("slf4jVersion").get()
val junitJupiterVersion = providers.gradleProperty("junitJupiterVersion").get()
val junitPlatformLauncherVersion = providers.gradleProperty("junitPlatformLauncherVersion").get()
val assertjVersion = providers.gradleProperty("assertjVersion").get()
val hamcrestVersion = providers.gradleProperty("hamcrestVersion").get()
val jacksonDatabindVersion = providers.gradleProperty("jacksonDatabindVersion").get()
val servletApiVersion = providers.gradleProperty("servletApiVersion").get()
val jsonPathVersion = providers.gradleProperty("jsonPathVersion").get()

val isReleaseVersion = !version.toString().endsWith("-SNAPSHOT")
val centralUsername = providers.gradleProperty("centralUsername")
    .orElse(providers.environmentVariable("CENTRAL_USERNAME"))
val centralPassword = providers.gradleProperty("centralPassword")
    .orElse(providers.environmentVariable("CENTRAL_PASSWORD"))
val signingKey = providers.gradleProperty("signingKey")
    .orElse(providers.environmentVariable("SIGNING_KEY"))
val signingPassword = providers.gradleProperty("signingPassword")
    .orElse(providers.environmentVariable("SIGNING_PASSWORD"))
val remotePublishingEnabled = centralUsername.isPresent && centralPassword.isPresent
val signingEnabled = signingKey.isPresent && signingPassword.isPresent

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    compileOnly("org.springframework:spring-web:$springVersion")
    compileOnly("org.springframework:spring-context:$springVersion")
    compileOnly("org.springframework.boot:spring-boot:$springBootVersion")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
    api("org.slf4j:slf4j-api:$slf4jVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testImplementation("org.hamcrest:hamcrest:$hamcrestVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.springframework:spring-web:$springVersion")
    testImplementation("org.springframework:spring-webmvc:$springVersion")
    testImplementation("org.springframework:spring-context:$springVersion")
    testImplementation("org.springframework:spring-test:$springVersion")
    testImplementation("org.springframework.boot:spring-boot:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:$jacksonDatabindVersion")
    testImplementation("jakarta.servlet:jakarta.servlet-api:$servletApiVersion")
    testImplementation("com.jayway.jsonpath:json-path:$jsonPathVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitPlatformLauncherVersion")
    testRuntimeOnly("org.slf4j:slf4j-simple:$slf4jVersion")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addStringOption("Xdoclint:html,reference,syntax", "-quiet")
        addBooleanOption("Werror", true)
    }
}

tasks.register<Delete>("cleanupOldApi") {
    group = "build"
    description = "Deletes the deprecated api/ package and duplicate spring/model/ classes"
    val base = "src/main/java/no/kompilator/situs"
    delete("$base/api")
    delete("$base/spring/model/TestCase.java")
    delete("$base/spring/model/TestCaseResult.java")
    delete("$base/spring/model/TestSuite.java")
    delete("$base/spring/model/TestSuiteResult.java")
}

tasks.register<JavaExec>("runSuite") {
    group = "verification"
    description = "Runs the RuntimeTestSuiteRunnerMain to execute system integration test suites"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("no.kompilator.situs.runtime.RuntimeTestSuiteRunnerMain")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("Situs Java Library")
                description.set("A Java library for system integration testing under the Situs namespace")
                url.set(providers.gradleProperty("pomUrl"))
                licenses {
                    license {
                        name.set(providers.gradleProperty("pomLicenseName"))
                        url.set(providers.gradleProperty("pomLicenseUrl"))
                    }
                }
                developers {
                    developer {
                        id.set(providers.gradleProperty("pomDeveloperId"))
                        name.set(providers.gradleProperty("pomDeveloperName"))
                    }
                }
                scm {
                    url.set(providers.gradleProperty("pomScmUrl"))
                    connection.set(providers.gradleProperty("pomScmConnection"))
                    developerConnection.set(providers.gradleProperty("pomScmDeveloperConnection"))
                }
            }
        }
    }
    repositories {
        mavenLocal()
        if (remotePublishingEnabled) {
            maven {
                name = "central"
                val releasesRepoUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
                val snapshotsRepoUrl = uri("https://central.sonatype.com/repository/maven-snapshots/")
                url = if (isReleaseVersion) releasesRepoUrl else snapshotsRepoUrl
                credentials {
                    username = centralUsername.get()
                    password = centralPassword.get()
                }
            }
        }
    }
}

signing {
    if (signingEnabled) {
        useInMemoryPgpKeys(signingKey.get(), signingPassword.get())
        sign(publishing.publications["mavenJava"])
    }
}

tasks.withType<Sign>().configureEach {
    onlyIf { signingEnabled }
}
