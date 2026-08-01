package app.ledger.server

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Real Postgres, not H2 and not a mock. The whole point of testing this layer is the SQL — the
 * CHECK constraints, the partial unique indexes and Hibernate's schema validation are exactly the
 * things an in-memory substitute would quietly not have.
 *
 * Deliberately *not* annotated `@Testcontainers`: that extension stops the container when the test
 * class it is attached to finishes, which for a container shared by several classes means every
 * class after the first talks to a dead database. Starting it once here and letting Testcontainers'
 * own reaper remove it when the JVM exits is what makes one Postgres serve the whole suite.
 *
 * Runs under the `dev` profile to get [app.ledger.server.identity.MockIdentityProvider]; the
 * datasource in `application-dev.yaml` is overridden by [ServiceConnection], which contributes a
 * ConnectionDetails bean and those beat properties.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
abstract class PostgresTest {
    companion object {
        @JvmStatic
        @ServiceConnection
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:18-alpine").apply { start() }
    }
}
