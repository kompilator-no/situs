plugins {
    id("com.gradle.develocity") version "4.3.2"
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
