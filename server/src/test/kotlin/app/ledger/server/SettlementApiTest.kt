package app.ledger.server

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Settle-up screen (§7a): bilateral rows, Pay as a request rather than an act, and Remind.
 *
 * The identity that matters is the second of the two invariants — a person's rows against everyone
 * else must sum to minus their overall position. If that breaks, the screen silently lies about
 * who owes what, which is the one failure the app cannot survive.
 */
class SettlementApiTest : ApiTest() {
    @Test
    fun `each row is your position with one person, and the rows sum to minus your net`() {
        val trip = threeWayTrip()

        val settlement = trip.alice.get("/api/trips/${trip.id}/settlement").json()

        val rows = settlement["rows"]
        assertEquals(2, rows.size(), "one row per other person, not a minimised transfer set")
        // The identity from §7a, over HTTP. It is a property test in the engine; this is the
        // assurance that the adapter did not lose it on the way out.
        assertEquals(
            -settlement["yourNetMinor"].asLong(),
            rows.sumOf { it["owedMinor"].asLong() },
            "the settle-up column does not agree with the hero figure",
        )
    }

    @Test
    fun `a row says which way the money goes`() {
        // Alice fronted $90 for three, so each of the others owes her $30.
        val trip = threeWayTrip()

        val alicesRows = trip.alice.rows(trip.id)
        val bobsRows = trip.bob.rows(trip.id)

        assertEquals(-3_000, alicesRows.getValue(trip.bobMember), "negative means they owe you")
        assertEquals(3_000, bobsRows.getValue(trip.aliceMember), "positive means you owe them")
    }

    @Test
    fun `tapping Pay settles nothing on its own`() {
        val trip = threeWayTrip()

        val paid = trip.bob.post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
        )

        assertEquals(HttpStatus.CREATED, paid.statusCode)
        assertEquals("PENDING", paid.json()["status"].asText())
        assertTrue(paid.json()["itemId"].isNull, "a settlement belongs to no single bill")
        // Still owed, because Alice has not agreed. This is why display-only settling was dropped.
        assertEquals(3_000, trip.bob.rows(trip.id).getValue(trip.aliceMember))
        assertFalse(
            trip.bob
                .get("/api/trips/${trip.id}/settlement")
                .json()["allSquare"]
                .asBoolean(),
        )
    }

    @Test
    fun `the row shows the claim waiting on the other person`() {
        val trip = threeWayTrip()
        trip.bob.post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
        )

        val bobsRow = trip.bob.rowFor(trip.id, trip.aliceMember)
        val alicesRow = trip.alice.rowFor(trip.id, trip.bobMember)

        // Both ends can see it: Bob's "sent for confirmation", Alice's "waiting on you".
        assertEquals(1, bobsRow["pending"].size())
        assertEquals(1, alicesRow["pending"].size())
    }

    @Test
    fun `approving the settlement is what clears the row`() {
        val trip = threeWayTrip()
        val settlement = trip.bob
            .post(
                "/api/trips/${trip.id}/settlements",
                mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
            ).id()

        trip.alice.post("/api/paybacks/$settlement/approve", emptyMap<String, String>())

        assertEquals(0, trip.bob.rows(trip.id).getValue(trip.aliceMember))
        assertEquals(0, trip.alice.rows(trip.id).getValue(trip.bobMember))
        // Carol still owes, so the trip is not square yet — all-square is derived, never a button.
        assertFalse(
            trip.alice
                .get("/api/trips/${trip.id}/settlement")
                .json()["allSquare"]
                .asBoolean(),
        )
    }

    @Test
    fun `a trip goes all square only when every row is clear`() {
        val trip = threeWayTrip()

        listOf(trip.bob, trip.carol).forEach { debtor ->
            val settlement = debtor
                .post(
                    "/api/trips/${trip.id}/settlements",
                    mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
                ).id()
            trip.alice.post("/api/paybacks/$settlement/approve", emptyMap<String, String>())
        }

        assertTrue(
            trip.alice
                .get("/api/trips/${trip.id}/settlement")
                .json()["allSquare"]
                .asBoolean(),
        )
        assertEquals(
            0,
            trip.alice
                .get("/api/trips/${trip.id}")
                .json()["yourNetMinor"]
                .asLong(),
        )
    }

    @Test
    fun `an item repayment and a settlement between the same two people do not both subtract`() {
        // One record type, one state machine (§5). This is the double-subtraction that having a
        // separate settlements table would have invited.
        val trip = threeWayTrip()
        val repayment = trip.bob
            .post(
                "/api/items/${trip.item}/paybacks",
                mapOf("fromMemberId" to trip.bobMember.toString(), "amountMinor" to 1_000, "paidOn" to "2026-08-02"),
            ).id()
        trip.alice.post("/api/paybacks/$repayment/approve", emptyMap<String, String>())

        val settlement = trip.bob
            .post(
                "/api/trips/${trip.id}/settlements",
                mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 2_000),
            ).id()
        trip.alice.post("/api/paybacks/$settlement/approve", emptyMap<String, String>())

        assertEquals(0, trip.bob.rows(trip.id).getValue(trip.aliceMember), "3000 owed, 3000 paid, in two pieces")
    }

    @Test
    fun `undoing an approved settlement un-settles the trip`() {
        val trip = threeWayTrip()
        val settlement = trip.bob
            .post(
                "/api/trips/${trip.id}/settlements",
                mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
            ).id()
        trip.alice.post("/api/paybacks/$settlement/approve", emptyMap<String, String>())

        trip.bob.post("/api/paybacks/$settlement/undo", emptyMap<String, String>())

        assertEquals(3_000, trip.bob.rows(trip.id).getValue(trip.aliceMember))
    }

    @Test
    fun `paying more than you owe is allowed`() {
        // Rounding a debt up, or covering something not yet entered. Refusing would be the app
        // telling somebody they are wrong about their own money.
        val trip = threeWayTrip()

        val paid = trip.bob.post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 5_000),
        )

        assertEquals(HttpStatus.CREATED, paid.statusCode)
    }

    @Test
    fun `you cannot settle with yourself, or with somebody not on the trip`() {
        val trip = threeWayTrip()
        val elsewhere = threeWayTrip()

        assertEquals(
            HttpStatus.BAD_REQUEST,
            trip.bob
                .post(
                    "/api/trips/${trip.id}/settlements",
                    mapOf("toMemberId" to trip.bobMember.toString(), "amountMinor" to 100),
                ).statusCode,
        )
        assertEquals(
            HttpStatus.BAD_REQUEST,
            trip.bob
                .post(
                    "/api/trips/${trip.id}/settlements",
                    mapOf("toMemberId" to elsewhere.bobMember.toString(), "amountMinor" to 100),
                ).statusCode,
        )
    }

    @Test
    fun `a reminder only goes to somebody who actually owes you`() {
        val trip = threeWayTrip()

        val nudge = trip.alice.post("/api/trips/${trip.id}/remind", mapOf("memberId" to trip.bobMember.toString()))
        val backwards = trip.bob.post("/api/trips/${trip.id}/remind", mapOf("memberId" to trip.aliceMember.toString()))

        assertEquals(HttpStatus.NO_CONTENT, nudge.statusCode)
        // Bob owes Alice, not the other way round, so there is nothing to nudge her about.
        assertEquals(HttpStatus.BAD_REQUEST, backwards.statusCode)
    }

    @Test
    fun `a reminder writes no payback and moves no balance`() {
        val trip = threeWayTrip()
        val before = trip.alice
            .get("/api/trips/${trip.id}")
            .json()["yourNetMinor"]
            .asLong()

        trip.alice.post("/api/trips/${trip.id}/remind", mapOf("memberId" to trip.bobMember.toString()))

        assertEquals(
            before,
            trip.alice
                .get("/api/trips/${trip.id}")
                .json()["yourNetMinor"]
                .asLong(),
        )
        assertEquals(0, trip.alice.rowFor(trip.id, trip.bobMember)["pending"].size())
    }

    @Test
    fun `the creator cannot wave through a settlement they are the one paying`() {
        // Alice created the trip, so she can approve on the group's behalf — but not a payment she
        // herself is making to somebody who can speak for themselves. Bob is a signed-in member; his
        // is the only agreement that counts here, and the creator hat does not override that.
        val trip = threeWayTrip()
        val claim = trip.alice
            .post(
                "/api/trips/${trip.id}/settlements",
                mapOf("toMemberId" to trip.bobMember.toString(), "amountMinor" to 3_000),
            ).id()

        val selfApprove = trip.alice.post("/api/paybacks/$claim/approve", emptyMap<String, String>())

        assertEquals(HttpStatus.FORBIDDEN, selfApprove.statusCode)
        // The claim is still waiting on Bob, and Bob can still approve it himself.
        val stillPending = trip.bob.rowFor(trip.id, trip.aliceMember)["pending"].single()
        assertEquals(claim.toString(), stillPending["id"].asText())
        assertEquals("PENDING", stillPending["status"].asText())
        assertEquals(
            HttpStatus.OK,
            trip.bob.post("/api/paybacks/$claim/approve", emptyMap<String, String>()).statusCode,
        )
    }

    @Test
    fun `the strip tells each person what they may do with a pending settlement`() {
        val trip = threeWayTrip()
        trip.bob.post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
        )

        // Alice, the person owed, may approve or reject it — and undo it.
        val aliceSees = trip.alice.rowFor(trip.id, trip.bobMember)["pending"].single()
        assertTrue(aliceSees["viewerCanDecide"].asBoolean(), "the recipient may decide")
        assertTrue(aliceSees["viewerCanUndo"].asBoolean())

        // Bob, the one paying, may not wave through his own — only withdraw it.
        val bobSees = trip.bob.rowFor(trip.id, trip.aliceMember)["pending"].single()
        assertFalse(bobSees["viewerCanDecide"].asBoolean(), "the payer cannot decide their own")
        assertTrue(bobSees["viewerCanUndo"].asBoolean(), "but may withdraw it")
    }

    @Test
    fun `an approved settlement stays on the strip as an undoable record for both people`() {
        val trip = threeWayTrip()
        val claim = trip.bob
            .post(
                "/api/trips/${trip.id}/settlements",
                mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
            ).id()
        trip.alice.post("/api/paybacks/$claim/approve", emptyMap<String, String>())

        // It has moved the balance, and it stays visible as a settled, undoable record — so a
        // mistaken confirmation is not a one-way door (§7a).
        val bobRow = trip.bob.rowFor(trip.id, trip.aliceMember)
        assertEquals(0, bobRow["owedMinor"].asLong(), "the balance moved")
        val settled = bobRow["settled"].single()
        assertEquals("APPROVED", settled["status"].asText())
        assertTrue(settled["viewerCanUndo"].asBoolean(), "either party can undo it")
        assertEquals(1, trip.alice.rowFor(trip.id, trip.bobMember)["settled"].size(), "and the recipient sees it too")
    }

    @Test
    fun `a declined settlement comes back to the claimant with the reason`() {
        // A trip-level settlement has no bill sheet, so a decline used to be filtered out of the
        // payload and simply vanish — the claimant never learned it was turned down, or why. It now
        // lands on the row, so the reason reaches the person who made the claim.
        val trip = threeWayTrip()
        val claim = trip.bob
            .post(
                "/api/trips/${trip.id}/settlements",
                mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
            ).id()

        trip.alice.post("/api/paybacks/$claim/reject", mapOf("reason" to "not received"))

        val bobRow = trip.bob.rowFor(trip.id, trip.aliceMember)
        assertEquals(0, bobRow["pending"].size(), "no longer waiting on anyone")
        assertEquals(3_000, bobRow["owedMinor"].asLong(), "the debt is back, undecided")
        val declined = bobRow["rejected"].single()
        assertEquals("REJECTED", declined["status"].asText())
        assertEquals("not received", declined["rejectReason"].asText())

        // The person who declined it is not shown their own decline echoed back at them.
        assertEquals(0, trip.alice.rowFor(trip.id, trip.bobMember)["rejected"].size())
    }

    @Test
    fun `a settlement larger than any real trip is refused`() {
        val trip = threeWayTrip()

        val response = trip.bob.post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 1_000_000_000_001),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a stranger sees no settlement at all`() {
        val trip = threeWayTrip()

        assertEquals(HttpStatus.NOT_FOUND, signedIn("Stranger").get("/api/trips/${trip.id}/settlement").statusCode)
    }

    // --- helpers -----------------------------------------------------------------------------

    private class Fixture(
        val alice: SessionAwareClient,
        val bob: SessionAwareClient,
        val carol: SessionAwareClient,
        val id: UUID,
        val aliceMember: UUID,
        val bobMember: UUID,
        val item: UUID,
    )

    /** Alice fronts $90 shared three ways, so Bob and Carol owe her $30 each. */
    private fun threeWayTrip(): Fixture {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val aliceMember = UUID.fromString(
            alice
                .get("/api/trips/$tripId")
                .json()["members"]
                .first { it["isYou"].asBoolean() }["id"]
                .asText(),
        )
        val bobMember = alice.addMember(tripId, "Bob")
        val carolMember = alice.addMember(tripId, "Carol")
        val invite = alice.invite(tripId)
        val bob = signedIn("Bob").also { it.claim(tripId, invite, bobMember) }
        val carol = signedIn("Carol").also { it.claim(tripId, invite, carolMember) }

        val item = alice
            .post(
                "/api/trips/$tripId/items",
                expense(
                    "Dinner",
                    9_000,
                    alice.builtInCategory(tripId),
                    aliceMember,
                    listOf(aliceMember, bobMember, carolMember),
                ),
            ).id()

        return Fixture(alice, bob, carol, tripId, aliceMember, bobMember, item)
    }

    private fun SessionAwareClient.rows(tripId: UUID): Map<UUID, Long> =
        get("/api/trips/$tripId/settlement")
            .json()["rows"]
            .associate { UUID.fromString(it["memberId"].asText()) to it["owedMinor"].asLong() }

    private fun SessionAwareClient.rowFor(tripId: UUID, memberId: UUID) =
        get("/api/trips/$tripId/settlement")
            .json()["rows"]
            .first { it["memberId"].asText() == memberId.toString() }
}
