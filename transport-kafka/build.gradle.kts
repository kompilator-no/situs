plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":framework-core"))
    testImplementation(testFixtures(project(":framework-core")))
}
