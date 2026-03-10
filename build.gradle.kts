// Root build file — shared configuration lives here.
// Subproject-specific config stays in each subproject's build.gradle.kts.

plugins {
    java
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }
}

tasks.register("releaseCheck") {
    group = "release"
    description = "Runs the verification tasks required before publishing artifacts."
    dependsOn(
            ":situs:test",
            ":plugins:test",
            ":java-spring-boot-sample-app:test",
            ":kotlin-spring-boot-sample-app:test",
            ":situs:javadoc",
            ":plugins:javadoc"
    )
}

tasks.register("testAll") {
    group = "verification"
    description = "Runs tests for all subprojects."
    dependsOn(
            ":situs:test",
            ":plugins:test",
            ":java-spring-boot-sample-app:test",
            ":kotlin-spring-boot-sample-app:test"
    )
}

tasks.register("buildAll") {
    group = "build"
    description = "Builds all subprojects."
    dependsOn(
            ":situs:build",
            ":plugins:build",
            ":java-spring-boot-sample-app:build",
            ":kotlin-spring-boot-sample-app:build"
    )
}

tasks.register("publishAllToMavenLocal") {
    group = "release"
    description = "Publishes the releasable modules to the local Maven repository."
    dependsOn(
            ":situs:publishToMavenLocal",
            ":plugins:publishToMavenLocal"
    )
}

tasks.register("publishRelease") {
    group = "release"
    description = "Runs release verification and publishes the releasable modules."
    dependsOn("releaseCheck", ":situs:publish", ":plugins:publish")
}
