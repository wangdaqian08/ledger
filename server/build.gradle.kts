plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
}

// Gradle's own platform support rather than the io.spring.dependency-management plugin: Spring's
// Gradle plugin docs list both and note the native route builds faster. Consequence — every
// Spring/Flyway/Testcontainers coordinate in libs.versions.toml is deliberately versionless.
dependencies {
    implementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.boot.bom))

    // The business rules. `server` is an adapter around this and owns no arithmetic of its own —
    // see docs/specs/2026-07-31-ledger-design.md §4.
    implementation(project(":engine"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.session.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.jackson.module.kotlin)
    implementation(kotlin("reflect"))

    // Flyway 10 split database support out of the core jar; without this, Postgres migrations
    // fail at startup rather than at compile time.
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(kotlin("test"))
}

// kotlin-jpa gives entities a no-arg constructor; it does not unfinal them. Hibernate needs
// non-final classes to proxy, so the entity annotations are opened here too.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

// :server:test needs a working Docker daemon — the tests run real Postgres via Testcontainers,
// because permission rules and the approval state machine are only proven against real SQL.
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}

// The deployable jar carries the SPA (spec §4). web/ stays a plain npm project — this copies its
// *build output*, it does not make it a Gradle module. The target must be BOOT-INF/classes/static:
// a jar-root static/ is invisible to the boot loader's classpath. Deploy builds pass
// -PrequireSpa=<base> to refuse a jar whose bundle is missing or built for the wrong base;
// without the flag (CI, local checks) an absent web/dist is simply not embedded.
tasks.bootJar {
    archiveFileName = "ledger.jar"
    val spaDist = layout.projectDirectory.dir("../web/dist")
    from(spaDist) { into("BOOT-INF/classes/static") }
    val requiredBase = providers.gradleProperty("requireSpa")
    doFirst {
        if (requiredBase.isPresent) {
            val index = spaDist.file("index.html").asFile
            check(index.exists()) {
                "web/dist is missing — run: VITE_BASE=${requiredBase.get()} npm --prefix web run build"
            }
            check(index.readText().contains("${requiredBase.get()}assets/")) {
                "web/dist was not built with base=${requiredBase.get()} — rebuild before packaging"
            }
        }
    }
}
