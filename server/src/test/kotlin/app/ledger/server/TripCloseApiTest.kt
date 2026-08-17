package app.ledger.server

import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Ending a trip (spec §3 · §5). The trip's record of spending closes; the settling of it does not.
 * Ending is the creator's call, reversible, and is what starts the 14-day clock on receipt images.
 */
class TripCloseApiTest : ApiTest() {
    @Test
    fun `the creator ends a trip, the state survives a reload, and reopening clears it`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip("Snow Trip")

        val closed = creator.post("/api/trips/$tripId/close", emptyMap<String, String>())
        assertEquals(HttpStatus.OK, closed.statusCode, "closing failed: ${closed.body}")
        val closedAt = closed.json()["closedAt"]
        assertNotNull(closedAt, "TripView should say when the trip ended")
        assertTrue(closedAt.isTextual, "closedAt should be the moment the trip ended, got $closedAt")

        assertTrue(
            creator.get("/api/trips/$tripId").json()["closedAt"].isTextual,
            "the ended state must survive a reload",
        )

        val reopened = creator.post("/api/trips/$tripId/reopen", emptyMap<String, String>())
        assertEquals(HttpStatus.OK, reopened.statusCode, "reopening failed: ${reopened.body}")
        assertTrue(reopened.json()["closedAt"].isNull, "reopening clears the end mark")
    }

    @Test
    fun `an open trip carries closedAt as null so no client has to guess`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()

        val closedAt = creator.get("/api/trips/$tripId").json()["closedAt"]
        assertNotNull(closedAt, "TripView should carry closedAt even while the trip is open")
        assertTrue(closedAt.isNull, "an open trip has not ended")
    }

    @Test
    fun `only the creator may end or reopen — a member gets 403, a stranger 404`() {
        val creator = signedIn("Nora")
        val friend = signedIn("Piet")
        val stranger = signedIn("Sam")
        val tripId = creator.createTrip()
        val seat = creator.addMember(tripId, "Piet")
        friend.claim(tripId, creator.invite(tripId), seat)

        val close = "/api/trips/$tripId/close"
        assertEquals(HttpStatus.FORBIDDEN, friend.post(close, emptyMap<String, String>()).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, stranger.post(close, emptyMap<String, String>()).statusCode)

        creator.post(close, emptyMap<String, String>())

        val reopen = "/api/trips/$tripId/reopen"
        assertEquals(HttpStatus.FORBIDDEN, friend.post(reopen, emptyMap<String, String>()).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, stranger.post(reopen, emptyMap<String, String>()).statusCode)
    }

    @Test
    fun `ending twice, or reopening an open trip, is a conflict not a shrug`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()

        assertEquals(
            HttpStatus.CONFLICT,
            creator.post("/api/trips/$tripId/reopen", emptyMap<String, String>()).statusCode,
            "reopening a trip that never ended",
        )
        creator.post("/api/trips/$tripId/close", emptyMap<String, String>())
        assertEquals(
            HttpStatus.CONFLICT,
            creator.post("/api/trips/$tripId/close", emptyMap<String, String>()).statusCode,
            "ending a trip that already ended",
        )
    }

    @Test
    fun `an ended trip refuses new or changed expenses until it is reopened`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()
        val me = creator.yourMemberId(tripId)
        val food = creator.builtInCategory(tripId)
        val existing = creator.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me))).id()

        creator.post("/api/trips/$tripId/close", emptyMap<String, String>())

        val refused = creator.post("/api/trips/$tripId/items", expense("Late bar", 1200, food, me, listOf(me)))
        assertEquals(HttpStatus.CONFLICT, refused.statusCode, "a fresh expense on an ended trip")
        assertTrue(
            refused.json()["detail"].asText().contains("ended"),
            "the reason should say the trip has ended, got: ${refused.body}",
        )
        assertEquals(
            HttpStatus.CONFLICT,
            creator.patch("/api/items/$existing", mapOf("title" to "Big dinner")).statusCode,
            "editing an expense on an ended trip",
        )
        assertEquals(
            HttpStatus.CONFLICT,
            creator.delete("/api/items/$existing").statusCode,
            "deleting an expense on an ended trip",
        )

        creator.post("/api/trips/$tripId/reopen", emptyMap<String, String>())
        assertEquals(
            HttpStatus.OK,
            creator.patch("/api/items/$existing", mapOf("title" to "Big dinner")).statusCode,
            "reopening puts the expenses back in reach",
        )
    }

    @Test
    fun `settling continues after a trip ends — the spending record closes, the debts do not`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()
        val me = creator.yourMemberId(tripId)
        val bob = creator.addMember(tripId, "Bob")
        val food = creator.builtInCategory(tripId)
        val itemId = creator.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me, bob))).id()

        creator.post("/api/trips/$tripId/close", emptyMap<String, String>())

        // The payer records that Bob squared up after everyone flew home — the whole reason
        // receipts stay readable for 14 days past the end.
        val payback = creator.post(
            "/api/items/$itemId/paybacks",
            mapOf("fromMemberId" to bob.toString(), "amountMinor" to 1500, "paidOn" to "2026-08-20"),
        )
        assertEquals(HttpStatus.CREATED, payback.statusCode, "paybacks must outlive the trip: ${payback.body}")
        assertEquals(
            "APPROVED",
            payback.json()["status"].asText(),
            "recorded by the person owed, so approved on the spot",
        )

        val settlement = creator.post(
            "/api/trips/$tripId/settlements",
            mapOf("toMemberId" to bob.toString(), "amountMinor" to 200),
        )
        assertEquals(HttpStatus.CREATED, settlement.statusCode, "settle-up must outlive the trip: ${settlement.body}")
    }
}
