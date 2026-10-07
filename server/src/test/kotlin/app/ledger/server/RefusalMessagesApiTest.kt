package app.ledger.server

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.util.UUID
import kotlin.test.assertEquals

/**
 * The status and the reason the client is given when it is refused, pinned together. The SPA shows
 * the reason to a person, so a refactor that kept the status and lost the words is still a change.
 */
class RefusalMessagesApiTest : ApiTest() {
    private fun ResponseEntity<String>.assertRefused(status: HttpStatus, detail: String, label: String) {
        assertEquals(status, statusCode, "$label: $body")
        assertEquals(detail, json()["detail"].asText(), label)
    }

    @Test
    fun `settle-up refuses strangers, outsiders and yourself in so many words`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val you = alice.yourMemberId(tripId)
        val bob = alice.addMember(tripId, "Bob")
        val nobody = UUID.randomUUID().toString()
        val stranger = signedIn("Mallory")

        stranger.get("/api/trips/$tripId/settlement").assertRefused(HttpStatus.NOT_FOUND, "No such trip", "view")
        stranger
            .post("/api/trips/$tripId/settlements", mapOf("toMemberId" to bob.toString(), "amountMinor" to 100))
            .assertRefused(HttpStatus.NOT_FOUND, "No such trip", "pay")
        stranger
            .post("/api/trips/$tripId/remind", mapOf("memberId" to bob.toString()))
            .assertRefused(HttpStatus.NOT_FOUND, "No such trip", "remind")
        stranger
            .post("/api/trips/$tripId/families", mapOf("families" to emptyList<Any>()))
            .assertRefused(HttpStatus.NOT_FOUND, "No such trip", "families")

        alice
            .post("/api/trips/$tripId/settlements", mapOf("toMemberId" to nobody, "amountMinor" to 100))
            .assertRefused(HttpStatus.BAD_REQUEST, "That person is not on this trip", "pay an outsider")
        alice
            .post("/api/trips/$tripId/settlements", mapOf("toMemberId" to you.toString(), "amountMinor" to 100))
            .assertRefused(HttpStatus.BAD_REQUEST, "You cannot settle up with yourself", "pay yourself")
        alice
            .post("/api/trips/$tripId/remind", mapOf("memberId" to nobody))
            .assertRefused(HttpStatus.BAD_REQUEST, "That person is not on this trip", "remind an outsider")
        alice
            .post("/api/trips/$tripId/remind", mapOf("memberId" to you.toString()))
            .assertRefused(HttpStatus.BAD_REQUEST, "You cannot remind yourself", "remind yourself")
    }

    @Test
    fun `an expense that does not exist is the same 404 everywhere`() {
        val alice = signedIn("Alice")
        val missing = UUID.randomUUID()

        alice.get("/api/items/$missing").assertRefused(HttpStatus.NOT_FOUND, "No such expense", "detail")
        alice.get("/api/items/$missing/receipt").assertRefused(HttpStatus.NOT_FOUND, "No such expense", "receipt")
        alice
            .post(
                "/api/items/$missing/paybacks",
                mapOf("fromMemberId" to UUID.randomUUID().toString(), "amountMinor" to 100, "paidOn" to "2026-08-01"),
            ).assertRefused(HttpStatus.NOT_FOUND, "No such expense", "payback")
    }

    @Test
    fun `a share link to a deleted trip is a trip that does not exist`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val token = alice.invite(tripId)
        alice.delete("/api/trips/$tripId")

        signedIn("Friend")
            .post("/api/trips/$tripId/claimable", mapOf("token" to token))
            .assertRefused(HttpStatus.NOT_FOUND, "No such trip", "claimable")
    }
}
