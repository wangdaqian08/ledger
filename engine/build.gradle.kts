plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The engine is the business rule set: split, settle, item state.
// It has NO production dependencies — not Spring, not a database, not even a JSON library.
// That is deliberate: the acceptance scenarios in docs/specs run in milliseconds.
dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}
