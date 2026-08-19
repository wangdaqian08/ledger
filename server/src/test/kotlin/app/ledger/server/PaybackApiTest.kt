package app.ledger.server

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Submit, approve, reject, correct, undo — and the rule the whole thing turns on: a claim the
 * person owed has not agreed to moves no number anywhere.
 */
class PaybackApiTest : ApiTest() {
    @Test
    fun `a pending claim moves no number at all`() {
        val trip = twoPeopleAndABill()

        val claim = trip.bob.post(
            "/api/items/${trip.item}/paybacks",
            claimOf(trip.bobMember, 5_000),
        )

        assertEquals(HttpStatus.CREATED, claim.statusCode)
        assertEquals("PENDING", claim.json()["status"].asText())
        // This is the rule. Until Alice agrees, Bob still owes every cent of it.
        assertEquals(-5_000, trip.bob.netOn(trip.id))
        assertEquals(5_000, trip.alice.netOn(trip.id))
        assertEquals("OPEN", trip.alice.itemState(trip.item))
    }

    @Test
    fun `approving it is what moves the money, and squares the bill`() {
        val trip = twoPeopleAndABill()
        val claim = trip.bob.post("/api/items/${trip.item}/paybacks", claimOf(trip.bobMember, 5_000)).id()

        val approved = trip.alice.post("/api/paybacks/$claim/approve", emptyMap<String, String>())

        assertEquals("APPROVED", approved.json()["status"].asText())
        assertEquals(0, trip.bob.netOn(trip.id))
        assertEquals(0, trip.alice.netOn(trip.id))
        // Every sharer bar the payer has covered their portion, so the card greys out.
        assertEquals("ALL_SQUARE", trip.alice.itemState(trip.item))
    }

    @Test
    fun `the person who is owed cannot be made to agree by the person who owes`() {
        val trip = twoPeopleAndABill()
        val claim = trip.bob.post("/api/items/${trip.item}/paybacks", claimOf(trip.bobMember, 5_000)).id()

        val self = trip.bob.post("/api/paybacks/$claim/approve", emptyMap<String, String>())

        assertEquals(HttpStatus.FORBIDDEN, self.statusCode)
        // Bob is the one paying, so the 403 names exactly that, rather than a bare status.
        assertTrue(self.body!!.contains("you are the one making"), "the 403 threw away its reason: ${self.body}")
        assertEquals(-5_000, trip.bob.netOn(trip.id), "a refused approval still moved money")
    }

    @Test
    fun `a payback larger than any real trip is refused`() {
        val trip = twoPeopleAndABill()

        val response = trip.bob.post(
            "/api/items/${trip.item}/paybacks",
            claimOf(trip.bobMember, 1_000_000_000_001),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `the payer ticking somebody off is agreed on the spot`() {
        // The person whose agreement is needed is the one recording it, so there is nobody left
        // to ask (spec §3). This is also how a friend who never signs in gets settled.
        val trip = twoPeopleAndABill()

        val ticked = trip.alice.post("/api/items/${trip.item}/paybacks", claimOf(trip.bobMember, 5_000))

        assertEquals("APPROVED", ticked.json()["status"].asText())
        assertEquals(0, trip.alice.netOn(trip.id))
    }

    @Test
    fun `a rejection says why, and the claimant can correct it and try again`() {
        val trip = twoPeopleAndABill()
        val claim = trip.bob.post("/api/items/${trip.item}/paybacks", claimOf(trip.bobMember, 9_000)).id()

        val rejected = trip.alice.post("/api/paybacks/$claim/reject", mapOf("reason" to "That was only 50"))
        assertEquals("REJECTED", rejected.json()["status"].asText())
        assertEquals("That was only 50", rejected.json()["rejectReason"].asText())
        assertEquals(-5_000, trip.bob.netOn(trip.id), "a rejected claim must move nothing")

        val corrected = trip.bob.patch("/api/paybacks/$claim", mapOf("amountMinor" to 5_000))

        assertEquals("PENDING", corrected.json()["status"].asText())
        assertTrue(corrected.json()["rejectReason"].isNull, "the old reason should not linger")
        assertEquals(-5_000, trip.bob.netOn(trip.id), "resubmitting is not approving")
    }

    @Test
    fun `a rejection has to say why`() {
        val trip = twoPeopleAndABill()
        val claim = trip.bob.post("/api/items/${trip.item}/paybacks", claimOf(trip.bobMember, 5_000)).id()

        val silent = trip.alice.post("/api/paybacks/$claim/reject", mapOf("reason" to "   "))

        assertEquals(HttpStatus.BAD_REQUEST, silent.statusCode)
    }

    @Test
    fun `a claim still awaiting review cannot be silently re-priced`() {
        val trip = twoPeopleAndABill()
        val claim = trip.bob.post("/api/items/${trip.item}/paybacks", claimOf(trip.bobMember, 5_000)).id()

        // Bob filed $50 and it is sitting PENDING in front of Alice. He must not be able to change
        // the amount she is looking at — correcting a claim is for one that was turned DOWN. Moving
        // the goalposts mid-review would let Alice approve a number she never saw.
        val moved = trip.bob.patch("/api/paybacks/$claim", mapOf("amountMinor" to 1))

        assertEquals(HttpStatus.CONFLICT, moved.statusCode)
        assertEquals(
            5_000,
            trip.alice
                .get("/api/items/${trip.item}")
                .json()["paybacks"]
                .single()["amountMinor"]
                .asLong(),
        )
        assertEquals(
            "PENDING",
            trip.alice
                .get("/api/items/${trip.item}")
                .json()["paybacks"]
                .single()["status"]
                .asText(),
        )
    }

    @Test
    fun `an approved claim cannot be quietly edited`() {
        val trip = twoPeopleAndABill()
        val claim = trip.bob.post("/api/items/${trip.item}/paybacks", claimOf(trip.bobMember, 5_000)).id()
        trip.alice.post("/api/paybacks/$claim/approve", emptyMap<String, String>())

        val edit = trip.bob.patch("/api/paybacks/$claim", mapOf("amountMinor" to 1))

        // Editing behind the approver's back is precisely what the two-party rule prevents.
        assertEquals(HttpStatus.CONFLICT, edit.statusCode)
        assertEquals(0, trip.bob.netOn(trip.id))
    }

    @Test
    fun `deciding twice is refused`() {
        val trip = twoPeopleAndABill()
        val claim = trip.bob.post("/api/items/${trip.item}/paybacks", claimOf(trip.bobMember, 5_000)).id()
        trip.alice.post("/api/paybacks/$claim/approve", emptyMap<String, String>())

        val again = trip.alice.post("/api/paybacks/$claim/reject", mapOf("reason" to "changed my mind"))

        assertEquals(HttpStatus.CONFLICT, again.statusCode)
    }

    @Test
    fun `either side can undo, before or after approval`() {
        val trip = twoPeopleAndABill()
        val claim = trip.bob.post("/api/items/${trip.item}/paybacks", claimOf(trip.bobMember, 5_000)).id()
        trip.alice.post("/api/paybacks/$claim/approve", emptyMap<String, String>())
        assertEquals(0, trip.bob.netOn(trip.id))

        // A settled trip can un-settle. That is the accepted cost of never trapping somebody in a
        // record they know to be wrong (§7a).
        assertEquals(
            HttpStatus.NO_CONTENT,
            trip.alice.post("/api/paybacks/$claim/undo", emptyMap<String, String>()).statusCode,
        )

        assertEquals(-5_000, trip.bob.netOn(trip.id), "undoing did not return the row to unpaid")
        assertEquals("OPEN", trip.alice.itemState(trip.item))
    }

    @Test
    fun `somebody not sharing the bill cannot pay it back`() {
        val trip = twoPeopleAndABill()
        val outsider = trip.alice.addMember(trip.id, "Outsider")

        val response = trip.alice.post("/api/items/${trip.item}/paybacks", claimOf(outsider, 1_000))

        // Money between two people who do not share this bill is a trip-level settlement — a
        // different record with a different meaning.
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `the payer cannot pay themselves back`() {
        val trip = twoPeopleAndABill()

        val response = trip.alice.post("/api/items/${trip.item}/paybacks", claimOf(trip.aliceMember, 1_000))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a claim for nothing is refused`() {
        val trip = twoPeopleAndABill()

        assertEquals(
            HttpStatus.BAD_REQUEST,
            trip.bob.post("/api/items/${trip.item}/paybacks", claimOf(trip.bobMember, 0)).statusCode,
        )
    }

    @Test
    fun `the detail sheet carries the claims, the trip payload does not`() {
        val trip = twoPeopleAndABill()
        trip.bob.post("/api/items/${trip.item}/paybacks", claimOf(trip.bobMember, 5_000))

        val detail = trip.alice.get("/api/items/${trip.item}").json()
        val inTrip = trip.alice.get("/api/trips/${trip.id}").json()["items"][0]

        assertEquals(1, detail["paybacks"].size())
        assertEquals("Hotel", detail["title"].asText(), "the item's own fields should sit alongside them")
        // Unbounded per item, and only the approval section wants them (spec §6).
        assertTrue(inTrip["paybacks"] == null, "the trip payload should not carry paybacks")
    }

    @Test
    fun `a stranger cannot see or touch a claim`() {
        val trip = twoPeopleAndABill()
        val claim = trip.bob.post("/api/items/${trip.item}/paybacks", claimOf(trip.bobMember, 5_000)).id()
        val stranger = signedIn("Stranger")

        assertEquals(
            HttpStatus.NOT_FOUND,
            stranger.post("/api/paybacks/$claim/approve", emptyMap<String, String>()).statusCode,
        )
        assertEquals(
            HttpStatus.NOT_FOUND,
            stranger.post("/api/paybacks/$claim/undo", emptyMap<String, String>()).statusCode,
        )
    }

    @Test
    fun `a member cannot file an item repayment in somebody else's name`() {
        // §5: the person who paid the money back files their own claim. A third sharer filing it as
        // though Bob had paid is neither Bob nor the person owed, so it is refused.
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val aliceMember = alice.yourMemberId(tripId)
        val bobMember = alice.addMember(tripId, "Bob")
        val carolMember = alice.addMember(tripId, "Carol")
        val invite = alice.invite(tripId)
        signedIn("Bob").also { it.claim(tripId, invite, bobMember) }
        val carol = signedIn("Carol").also { it.claim(tripId, invite, carolMember) }
        val item = alice
            .post(
                "/api/trips/$tripId/items",
                expense(
                    "Hotel",
                    9_000,
                    alice.builtInCategory(tripId, "stay"),
                    aliceMember,
                    listOf(aliceMember, bobMember, carolMember),
                ),
            ).id()

        // Carol files a repayment naming Bob as the one who paid — not herself, and she is not owed.
        val response = carol.post(
            "/api/items/$item/paybacks",
            claimOf(bobMember, 3_000),
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }

    @Test
    fun `the trip creator can approve an item repayment between two other people`() {
        // The creator's approval hat is not limited to trip-level settlements (§5). Bob fronts a
        // bill, Carol files that she paid him back, and Alice — the creator, party to neither side —
        // approves it, so a trip does not stall when the person owed stops answering.
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val bobMember = alice.addMember(tripId, "Bob")
        val carolMember = alice.addMember(tripId, "Carol")
        val invite = alice.invite(tripId)
        val bob = signedIn("Bob").also { it.claim(tripId, invite, bobMember) }
        val carol = signedIn("Carol").also { it.claim(tripId, invite, carolMember) }
        val item = bob
            .post(
                "/api/trips/$tripId/items",
                expense("Taxi", 8_000, alice.builtInCategory(tripId), bobMember, listOf(bobMember, carolMember)),
            ).id()

        val claim = carol.post("/api/items/$item/paybacks", claimOf(carolMember, 4_000)).id()
        assertEquals(
            "PENDING",
            carol
                .get("/api/items/$item")
                .json()["paybacks"]
                .single()["status"]
                .asText(),
            "filed by the one paying, so it waits on approval",
        )

        val approved = alice.post("/api/paybacks/$claim/approve", emptyMap<String, String>())

        assertEquals(HttpStatus.OK, approved.statusCode, "the creator may decide it: ${approved.body}")
        assertEquals("APPROVED", approved.json()["status"].asText())
    }

    // --- helpers -----------------------------------------------------------------------------

    private class Fixture(
        val alice: SessionAwareClient,
        val bob: SessionAwareClient,
        val id: UUID,
        val aliceMember: UUID,
        val bobMember: UUID,
        val item: UUID,
    )

    /** Alice fronts $100 for a room she and Bob share, so Bob owes her exactly $50. */
    private fun twoPeopleAndABill(): Fixture {
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
        val bob = signedIn("Bob")
        bob.claim(tripId, alice.invite(tripId), bobMember)

        val item = alice
            .post(
                "/api/trips/$tripId/items",
                expense(
                    "Hotel",
                    10_000,
                    alice.builtInCategory(tripId, "stay"),
                    aliceMember,
                    listOf(aliceMember, bobMember),
                ),
            ).id()

        return Fixture(alice, bob, tripId, aliceMember, bobMember, item)
    }

    private fun claimOf(fromMemberId: UUID, amountMinor: Long) = mapOf(
        "fromMemberId" to fromMemberId.toString(),
        "amountMinor" to amountMinor,
        "paidOn" to LocalDate.of(2026, 8, 2).toString(),
    )

    private fun SessionAwareClient.netOn(tripId: UUID): Long =
        get("/api/trips/$tripId").json()["yourNetMinor"].asLong()

    private fun SessionAwareClient.itemState(itemId: UUID): String =
        get("/api/items/$itemId").json()["state"].asText()
}
