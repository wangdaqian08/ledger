package app.ledger.server

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Shared scaffolding for tests that speak to the running application over HTTP.
 *
 * Everything here is deliberately thin: it signs people in and sets up trips, and does no asserting
 * of its own. A helper that quietly asserts is a helper that makes a failing test look like it
 * passed somewhere else.
 */
abstract class ApiTest : PostgresTest() {
    @LocalServerPort
    protected var port: Int = 0

    // Its own mapper, not the application's: this reads responses back, and borrowing the server's
    // would let a serialisation setting be wrong in both places at once and still pass.
    protected val json: ObjectMapper = ObjectMapper()

    /**
     * One Postgres serves the whole suite and the mock provider keys users on their name, so a
     * bare "Alice" in two tests would be one person holding both tests' trips.
     */
    protected fun signedIn(name: String): SessionAwareClient {
        val unique = "$name ${UUID.randomUUID().toString().take(8)}"
        val client = SessionAwareClient("http://localhost:$port")
        client.get("/api/me")
        val response = client.post("/api/auth/session", mapOf("idToken" to unique))
        check(response.statusCode == HttpStatus.OK) { "could not sign in as $unique: ${response.statusCode}" }
        return client
    }

    protected fun newTrip(name: String): Map<String, Any> = mapOf(
        "name" to name,
        "icon" to "plane",
        "hue" to 3,
        "currencyCode" to "AUD",
    )

    protected fun SessionAwareClient.createTrip(name: String = "Hokkaido"): UUID =
        post("/api/trips", newTrip(name)).id()

    protected fun SessionAwareClient.addMember(tripId: UUID, displayName: String): UUID =
        post("/api/trips/$tripId/members", mapOf("displayName" to displayName)).id()

    protected fun SessionAwareClient.invite(tripId: UUID): String {
        val response = post("/api/trips/$tripId/invite", emptyMap<String, String>())
        check(response.statusCode == HttpStatus.OK) { "could not issue an invite: ${response.statusCode}" }
        return json.readTree(response.body)["token"].asText()
    }

    protected fun SessionAwareClient.claim(tripId: UUID, token: String, memberId: UUID): ResponseEntity<String> =
        post("/api/trips/$tripId/claim", mapOf("token" to token, "memberId" to memberId.toString()))

    /** The id of a built-in category, so tests do not have to invent one. */
    protected fun SessionAwareClient.builtInCategory(tripId: UUID, key: String = "food"): UUID {
        val categories = get("/api/trips/$tripId/categories").json()
        return UUID.fromString(categories.first { it["key"].asText() == key }["id"].asText())
    }

    protected fun expense(
        title: String,
        amountMinor: Long,
        categoryId: UUID,
        payerMemberId: UUID,
        sharedBy: List<UUID>,
        splitRule: String = "EQUAL",
        spentOn: LocalDate = LocalDate.of(2026, 8, 1),
    ): Map<String, Any> = mapOf(
        "title" to title,
        "categoryId" to categoryId.toString(),
        "amountMinor" to amountMinor,
        "splitRule" to splitRule,
        "payerMemberId" to payerMemberId.toString(),
        "spentOn" to spentOn.toString(),
        "sharedBy" to sharedBy.map { mapOf("memberId" to it.toString()) },
    )

    protected fun ResponseEntity<String>.json(): JsonNode {
        assertNotNull(body, "expected a body, got $statusCode")
        return json.readTree(body)
    }

    protected fun ResponseEntity<String>.id(): UUID {
        assertFalse(statusCode.isError, "request failed with $statusCode: $body")
        return UUID.fromString(json()["id"].asText())
    }
}
