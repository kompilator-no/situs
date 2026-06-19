pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    val springBootVersion: String by settings
    val springDependencyManagementVersion: String by settings
    val kotlinVersion: String by settings

    plugins {
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version springDependencyManagementVersion
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion
    }
}

plugins {
    id("com.gradle.develocity") version "4.4.3"
}

rootProject.name = "situs"

include("situs")
include("plugins")
include("java-spring-boot-sample-app")
include("kotlin-spring-boot-sample-app")

develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
    }
}
