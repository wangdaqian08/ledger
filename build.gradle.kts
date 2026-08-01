import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.spotless)
}

allprojects {
    group = "app.ledger"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

val ktlintVersion = libs.versions.ktlint.get()

// Applied to every project, so `server` and `web` are covered the moment they are added to
// settings.gradle.kts — see docs/specs/2026-07-31-ledger-design.md §8.
//
// Which rules are on is decided in .editorconfig, which ktlint reads directly. Spotless wires
// spotlessCheck into `check`, so `./gradlew check` fails on unformatted code and
// `./gradlew spotlessApply` fixes it.
allprojects {
    apply(plugin = "com.diffplug.spotless")

    configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(ktlintVersion)
        }
        kotlinGradle {
            target("*.gradle.kts")
            // Mirrors the [*.gradle.kts] section of .editorconfig. ktlint's own CLI and the IDE
            // honour that section; Spotless does not resolve path-scoped sections for this
            // target, so the rule has to be named again here. Keep the two in step.
            ktlint(ktlintVersion)
                .editorConfigOverride(mapOf("ktlint_standard_chain-method-continuation" to "disabled"))
        }
    }
}
