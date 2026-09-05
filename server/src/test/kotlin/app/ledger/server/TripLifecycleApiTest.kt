package app.ledger.server

import com.fasterxml.jackson.databind.JsonNode
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.http.HttpStatus

/**
 * The whole product in one sitting. The per-feature suites prove each rule on its own; this one
 * proves they hold while interleaved, the way a real trip would hit them: two people signed in, a
 * third who never signs in, all three split rules on one trip, a partial repayment, a rejection
 * corrected and approved, an undo, the creator settling on behalf of the ghost — and finally a
 * settled bill deleted out from under everybody.
 *
 * After every step the books are read from BOTH signed-in viewpoints and must agree: each viewer's
 * rows sum to minus their net (invariant 2, over HTTP), and Alice's row about Bob is Bob's row
 * about Alice with the sign flipped. Every item id is pinned, so every cent asserted here is
 * derived, not observed.
 */
class TripLifecycleApiTest : ApiTest() {
    // msb == lsb, so the engine salt is zero and the spare cent lands on the first name listed.
    private val carHire = "cafebabe-dead-4eef-cafe-babedead4eef"
    private val dinner = "feedf00d-dead-4eef-feed-f00ddead4eef"
    private val groceries = "0ddba11s-0000-4000-8000-000000000001".replace("s", "0")
    private val museum = "0ddba110-0000-4000-8000-000000000002"

    @Test
    fun `a weekend away, from sign-in to a settled bill being deleted`() {
        // ---- The cast: Alice creates, Bob joins by link, Cara never signs in.
        val alice = signedIn("Alice")
        val trip = alice.createTrip("Lifecycle weekend")
        val aliceM = alice
            .get("/api/trips/$trip")
            .json()["members"]
            .single { it["isYou"].asBoolean() }
            .memberId()
        val bobM = alice.addMember(trip, "Bob")
        val caraM = alice.addMember(trip, "Cara")
        val bob = signedIn("Bob")
        assertEquals(HttpStatus.OK, bob.claim(trip, alice.invite(trip), bobM).statusCode)
        val category = alice.builtInCategory(trip)

        // ---- Four expenses, one per way of splitting.                    net effect per person
        alice
            .post(
                "/api/trips/$trip/items", // car hire 300.00, three ways        A +200.00  B −100.00  C −100.00
                expense("Car hire", 30_000, category, aliceM, listOf(aliceM, bobM, caraM)) + mapOf("id" to carHire),
            ).let { assertEquals(HttpStatus.CREATED, it.statusCode) }

        bob
            .post(
                "/api/trips/$trip/items", // dinner 100.01, two ways            A −50.00   B +50.00
                expense("Dinner", 10_001, category, bobM, listOf(bobM, aliceM)) + mapOf("id" to dinner),
            ).let {
                // Salt zero, equal fractions: the spare cent sits with the first name listed — Bob.
                assertEquals(listOf(5_001L, 5_000L), it.json()["splits"].map { s -> s["amountMinor"].asLong() })
            }

        alice
            .post(
                "/api/trips/$trip/items", // groceries 99.99 at 2:1:1           A −49.99   B −25.00   C +74.99
                expense("Groceries", 9_999, category, caraM, emptyList(), splitRule = "WEIGHTED") +
                    mapOf(
                        "id" to groceries,
                        "sharedBy" to listOf(
                            mapOf("memberId" to aliceM.toString(), "weight" to 2),
                            mapOf("memberId" to bobM.toString(), "weight" to 1),
                            mapOf("memberId" to caraM.toString(), "weight" to 1),
                        ),
                    ),
            ).let {
                // 9999·2/4 floors to 4999 with the smallest fraction; the two spare cents go to the 1s.
                assertEquals(listOf(4_999L, 2_500L, 2_500L), it.json()["splits"].map { s -> s["amountMinor"].asLong() })
            }

        alice
            .post(
                "/api/trips/$trip/items", // museum 75.00, named amounts        A +50.00   B −50.00
                expense("Museum", 7_500, category, aliceM, emptyList(), splitRule = "EXACT") +
                    mapOf(
                        "id" to museum,
                        "sharedBy" to listOf(
                            mapOf("memberId" to aliceM.toString(), "exactAmountMinor" to 2_500),
                            mapOf("memberId" to bobM.toString(), "exactAmountMinor" to 5_000),
                        ),
                    ),
            ).let { assertEquals(HttpStatus.CREATED, it.statusCode) }

        // Columns so far: Alice +150.01, Bob −125.00, Cara −25.01 — and pairwise:
        // Bob owes Alice 100.00, Cara owes Alice 50.01, Bob owes Cara 25.00.
        books(trip, alice to aliceM, bob to bobM)
        assertEquals(15_001, alice.net(trip))
        assertEquals(-10_000, alice.owedWith(trip, bobM))
        assertEquals(-5_001, alice.owedWith(trip, caraM))
        assertEquals(2_500, bob.owedWith(trip, caraM))

        // The trip payload and the settlement screen must tell the same story.
        assertEquals(15_001, alice.get("/api/trips/$trip").json()["yourNetMinor"].asLong())

        // ---- Bob covers part of the car hire. Pending first: it must move nothing.
        val partId = bob
            .post(
                "/api/items/$carHire/paybacks",
                mapOf("fromMemberId" to bobM.toString(), "amountMinor" to 4_000, "paidOn" to "2026-08-02"),
            ).id()
        assertEquals(-10_000, alice.owedWith(trip, bobM), "a claim nobody has agreed to moves nothing")
        // Item claims wait on the bill's own sheet; a settle-up row's pending list carries only
        // trip-level settlements. The detail sheet is where Alice sees this one.
        assertEquals(
            4_000,
            alice
                .get("/api/items/$carHire")
                .json()["paybacks"]
                .single()["amountMinor"]
                .asLong(),
            "but the person owed can see it waiting on the bill",
        )

        assertFalse(alice.post("/api/paybacks/$partId/approve", emptyMap<String, String>()).statusCode.isError)
        assertEquals(-6_000, alice.owedWith(trip, bobM), "approval is what moves the money")
        books(trip, alice to aliceM, bob to bobM)

        // A nudge is allowed while Bob still owes — and changes nothing at all.
        assertEquals(
            HttpStatus.NO_CONTENT,
            alice.post("/api/trips/$trip/remind", mapOf("memberId" to bobM.toString())).statusCode,
        )
        assertEquals(-6_000, alice.owedWith(trip, bobM))

        // ---- Bob clears the rest, a dollar too generously. Alice sends it back; he corrects it.
        val rest = bob
            .post(
                "/api/trips/$trip/settlements",
                mapOf("toMemberId" to aliceM.toString(), "amountMinor" to 6_001),
            ).id()
        assertFalse(alice.post("/api/paybacks/$rest/reject", mapOf("reason" to "a dollar too much")).statusCode.isError)
        assertEquals(-6_000, alice.owedWith(trip, bobM), "a rejected claim moves nothing either")
        assertFalse(bob.patch("/api/paybacks/$rest", mapOf("amountMinor" to 6_000)).statusCode.isError)
        assertFalse(alice.post("/api/paybacks/$rest/approve", emptyMap<String, String>()).statusCode.isError)
        assertEquals(0, alice.owedWith(trip, bobM))
        books(trip, alice to aliceM, bob to bobM)

        // ---- Bob squares up with Cara: a change of heart first, then for real. Cara has never
        // signed in, so the approval that lands is the creator's — §5's answer to a trip stalling.
        val changed = bob
            .post(
                "/api/trips/$trip/settlements",
                mapOf("toMemberId" to caraM.toString(), "amountMinor" to 2_500),
            ).id()
        assertFalse(bob.post("/api/paybacks/$changed/undo", emptyMap<String, String>()).statusCode.isError)
        assertEquals(2_500, bob.owedWith(trip, caraM), "an undone claim leaves the debt standing")

        val toCara = bob
            .post(
                "/api/trips/$trip/settlements",
                mapOf("toMemberId" to caraM.toString(), "amountMinor" to 2_500),
            ).id()
        assertFalse(alice.post("/api/paybacks/$toCara/approve", emptyMap<String, String>()).statusCode.isError)
        assertEquals(0, bob.owedWith(trip, caraM))
        books(trip, alice to aliceM, bob to bobM)

        // ---- Cara hands Alice her whole car-hire share in cash. The payer recording it is
        // agreement on the spot — nobody else's say-so is missing.
        val cash = alice.post(
            "/api/items/$carHire/paybacks",
            mapOf("fromMemberId" to caraM.toString(), "amountMinor" to 10_000, "paidOn" to "2026-08-03"),
        )
        assertEquals("APPROVED", cash.json()["status"].asText())
        assertEquals(4_999, alice.owedWith(trip, caraM), "Cara overshot: now Alice owes her the groceries share")

        // Alice pays that back. She files the claim and, as creator, is also the one who can
        // approve it — the accepted cost (§5) of Cara never signing in.
        val back = alice
            .post(
                "/api/trips/$trip/settlements",
                mapOf("toMemberId" to caraM.toString(), "amountMinor" to 4_999),
            ).id()
        assertFalse(alice.post("/api/paybacks/$back/approve", emptyMap<String, String>()).statusCode.isError)

        // ---- Everyone square, from both chairs.
        for ((viewer, label) in listOf(alice to "Alice", bob to "Bob")) {
            val view = viewer.settlement(trip)
            assertTrue(view["allSquare"].asBoolean(), "$label should see a square trip")
            assertEquals(0, view["yourNetMinor"].asLong(), "$label's net should be zero")
        }
        books(trip, alice to aliceM, bob to bobM)

        // The car hire itself is still OPEN: Bob's 60.00 arrived as trip-level settlements, and
        // trip money does not tick off bills. The screen is square; the bill remembers.
        assertEquals("OPEN", alice.get("/api/items/$carHire").json()["state"].asText())

        // ---- Alice deletes the car hire. Its own repayments (44.00 + 100.00) die with it; the
        // trip-level ones (60.00, 25.00, 49.99) survive. The books must rebalance on the spot:
        // Bob's surviving 85.00 now buys him less debt than he had, so the trip owes HIM.
        assertFalse(alice.delete("/api/items/$carHire").statusCode.isError)

        assertEquals(6_000, alice.owedWith(trip, bobM), "Alice now owes Bob his stranded settlement money")
        assertEquals(0, alice.owedWith(trip, caraM))
        assertEquals(-6_000, alice.net(trip))
        assertEquals(6_000, bob.net(trip))
        assertFalse(alice.settlement(trip)["allSquare"].asBoolean(), "square no longer")
        books(trip, alice to aliceM, bob to bobM)
    }

    /** Invariant 2 over HTTP, from every chair at once, plus both ends of each debt agreeing. */
    private fun books(tripId: UUID, vararg viewers: Pair<SessionAwareClient, UUID>) {
        val views = viewers.associate { (client, memberId) -> memberId to client.settlement(tripId) }
        for ((memberId, view) in views) {
            assertEquals(
                -view["yourNetMinor"].asLong(),
                view["rows"].sumOf { it["owedMinor"].asLong() },
                "the rows must sum to minus the viewer's net",
            )
            for (row in view["rows"]) {
                val mirror = views[row.memberId()]?.rowWith(memberId) ?: continue
                assertEquals(
                    -row["owedMinor"].asLong(),
                    mirror["owedMinor"].asLong(),
                    "both ends of one debt must state the same figure",
                )
            }
        }
    }

    private fun SessionAwareClient.settlement(tripId: UUID): JsonNode = get("/api/trips/$tripId/settlement").json()

    private fun SessionAwareClient.net(tripId: UUID): Long = settlement(tripId)["yourNetMinor"].asLong()

    /** Positive: the viewer owes them. Negative: they owe the viewer. */
    private fun SessionAwareClient.owedWith(tripId: UUID, other: UUID): Long =
        settlement(tripId).rowWith(other)["owedMinor"].asLong()

    private fun JsonNode.rowWith(memberId: UUID): JsonNode = get("rows").single { it.memberId() == memberId }

    private fun JsonNode.memberId(): UUID = UUID.fromString(get("memberId")?.asText() ?: get("id").asText())
}
