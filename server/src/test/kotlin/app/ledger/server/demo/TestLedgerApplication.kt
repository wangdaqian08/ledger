package app.ledger.server.demo

import app.ledger.server.LedgerApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Runs the real application against a real Postgres, started for you.
 *
 * ```
 * ./gradlew :server:bootTestRun
 * ```
 *
 * This lives in test sources on purpose: the demo seed alongside it can never be packaged into a
 * deployable jar, whatever profile somebody sets in production.
 *
 * H2 was considered and measured. It refuses fourteen statements of `V1__init.sql`, and the
 * blocking one is that it has no partial indexes at all — including the two that stop a trip
 * having two categories called "Food". Making it work would mean a second, weaker schema and a
 * demo that exercises constraints production does not have. One schema, one migration, real
 * Postgres.
 */
@TestConfiguration(proxyBeanMethods = false)
class DemoContainers {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:18-alpine")
}

fun main(args: Array<String>) {
    SpringApplicationBuilder(LedgerApplication::class.java)
        .sources(DemoContainers::class.java)
        // `dev` brings the mock identity provider and the local invite secret; `demo` seeds the
        // hotel scenario. Overridable on the command line if you want an empty database.
        .properties("spring.profiles.active=dev,demo")
        .run(*args)
}
