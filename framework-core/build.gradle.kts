plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-test-fixtures`
}

dependencies {
    testFixturesApi("org.junit.jupiter:junit-jupiter-api")
    testFixturesApi(kotlin("test"))
}
