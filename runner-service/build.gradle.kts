plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    implementation(project(":framework-core"))
    implementation(project(":transport-kafka"))
    implementation(project(":transport-http"))
    implementation(project(":transport-websocket"))
    implementation(project(":reporting-client"))
}

application {
    mainClass.set("no.testframework.runner.service.MainKt")
}
