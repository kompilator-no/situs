plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-test-fixtures`
}

dependencies {
    testFixturesApi("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testFixturesApi(kotlin("test"))
}
