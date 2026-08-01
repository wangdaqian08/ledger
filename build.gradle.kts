plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

allprojects {
    group = "app.ledger"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
