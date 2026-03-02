plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":framework-core"))
    implementation("org.apache.kafka:kafka-clients:3.9.0")
    testImplementation(testFixtures(project(":framework-core")))
}
