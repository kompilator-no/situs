// Root build file — shared configuration lives here.
// Subproject-specific config stays in each subproject's build.gradle.kts.

plugins {
    java
}

subprojects {
    repositories {
        mavenCentral()
    }
}
