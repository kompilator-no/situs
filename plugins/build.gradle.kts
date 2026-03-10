import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.Sign

plugins {
    `java-library`
    `maven-publish`
    signing
}

val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
val springVersion = providers.gradleProperty("springVersion").get()
val springBootVersion = providers.gradleProperty("springBootVersion").get()
val junitJupiterVersion = providers.gradleProperty("junitJupiterVersion").get()
val junitPlatformLauncherVersion = providers.gradleProperty("junitPlatformLauncherVersion").get()
val assertjVersion = providers.gradleProperty("assertjVersion").get()
val jacksonDatabindVersion = providers.gradleProperty("jacksonDatabindVersion").get()

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
    api(project(":situs"))
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonDatabindVersion")

    compileOnly("org.springframework.boot:spring-boot:$springBootVersion")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
    compileOnly("org.springframework:spring-context:$springVersion")

    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$junitPlatformLauncherVersion")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("Test Framework Plugins")
                description.set("Ready-made plugins for Situs system integration testing")
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
