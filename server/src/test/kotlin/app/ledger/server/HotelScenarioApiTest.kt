package app.ledger.server

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scenario the whole application exists for, over HTTP — spec §10's manual check, run as a
 * test rather than performed by hand.
 *
 * A hotel is booked for thirteen. Two bills of $1,000 each are paid by Bob. Then Jack turns up at
 * check-in, and the *only* thing anybody does about it is tick him onto both bills' people lists.
 * There is no deposit-stage concept, no rebalancing pass and no stored total to correct: every
 * share is derived on read, so correcting the list is the entire fix.
 */
class HotelScenarioApiTest : ApiTest() {
    private val thousandDollars = 100_000L

    @Test
    fun `ticking a late arrival onto both bills re-divides them for everybody`() {
        val bob = signedIn("Bob")
        val tripId = bob.createTrip("Hokkaido")
        val bobMember = bob.memberIdOf(tripId)
        val others = (1..12).map { bob.addMember(tripId, "Friend $it") }
        val thirteen = listOf(bobMember) + others
        val category = bob.builtInCategory(tripId, "stay")

        val deposit = bob
            .post(
                "/api/trips/$tripId/items",
                expense("Hotel deposit", thousandDollars, category, bobMember, thirteen),
            ).id()
        val balance = bob
            .post(
                "/api/trips/$tripId/items",
                expense("Hotel balance", thousandDollars, category, bobMember, thirteen),
            ).id()

        // Thirteen people, $2,000: nobody's share is a whole number of cents, so the largest
        // remainder has real work to do.
        val shareOfThirteen = bob.totalShare(tripId, of = others.first())
        assertEquals(2 * thousandDollars, bob.totalOwedAcross(tripId), "the two bills do not add up")
        assertTrue(shareOfThirteen in 15_384..15_386, "unexpected share of 2000/13: $shareOfThirteen")

        // Jack arrives. Adding him to the trip changes nothing at all — the roster is manual by
        // choice (spec §3), and this is the assertion that proves it.
        val jack = bob.addMember(tripId, "Jack")
        assertEquals(shareOfThirteen, bob.totalShare(tripId, of = others.first()), "adding Jack moved a number")
        assertEquals(0, bob.totalShare(tripId, of = jack), "Jack owes something before being ticked in")

        // The entire fix: correct both people lists.
        val fourteen = thirteen + jack
        listOf(deposit, balance).forEach { itemId ->
            val patched = bob.patch(
                "/api/items/$itemId",
                mapOf("sharedBy" to fourteen.map { mapOf("memberId" to it.toString()) }),
            )
            assertEquals(HttpStatus.OK, patched.statusCode, "could not correct the people list: ${patched.body}")
        }

        val shareOfFourteen = bob.totalShare(tripId, of = others.first())
        assertEquals(2 * thousandDollars, bob.totalOwedAcross(tripId), "a cent went missing in the re-split")
        assertTrue(shareOfFourteen in 14_285..14_287, "unexpected share of 2000/14: $shareOfFourteen")
        assertTrue(shareOfFourteen < shareOfThirteen, "the original thirteen should owe less, not more")
        assertTrue(bob.totalShare(tripId, of = jack) in 14_285..14_287, "Jack is not carrying a full share")
    }

    @Test
    fun `every share of every bill is accounted for, to the cent`() {
        // The first invariant, over HTTP: nobody's balance can be right if a bill does not add up.
        // Thirteen into $1,000 leaves four spare cents that have to land somewhere and be counted.
        val bob = signedIn("Bob")
        val tripId = bob.createTrip("Hokkaido")
        val bobMember = bob.memberIdOf(tripId)
        val roster = listOf(bobMember) + (1..12).map { bob.addMember(tripId, "Friend $it") }
        val category = bob.builtInCategory(tripId, "stay")

        bob.post("/api/trips/$tripId/items", expense("Hotel", thousandDollars, category, bobMember, roster))

        val splits = bob.items(tripId).single()["splits"]
        assertEquals(13, splits.size())
        assertEquals(thousandDollars, splits.sumOf { it["amountMinor"].asLong() })
        // Largest remainder, not rounding: every share is a floor or a floor plus one, never more.
        assertTrue(splits.all { it["amountMinor"].asLong() in 7_692..7_693 }, "a share was rounded, not remaindered")
    }

    @Test
    fun `the payer is owed exactly what everybody else owes`() {
        val bob = signedIn("Bob")
        val tripId = bob.createTrip("Hokkaido")
        val bobMember = bob.memberIdOf(tripId)
        val alice = bob.addMember(tripId, "Alice")
        val category = bob.builtInCategory(tripId, "stay")
        bob.post(
            "/api/trips/$tripId/items",
            expense("Hotel", thousandDollars, category, bobMember, listOf(bobMember, alice)),
        )

        // Alice signs in and sees her own position, which must be the mirror of Bob's.
        val aliceClient = signedIn("Alice")
        aliceClient.claim(tripId, bob.invite(tripId), alice)

        val bobsNet = bob.get("/api/trips/$tripId").json()["yourNetMinor"].asLong()
        val alicesNet = aliceClient.get("/api/trips/$tripId").json()["yourNetMinor"].asLong()

        assertEquals(50_000, bobsNet, "Bob fronted 1000 and owes half of it, so he is owed 500")
        assertEquals(-50_000, alicesNet)
        assertEquals(0, bobsNet + alicesNet, "balances must sum to zero")
    }

    /**
     * Spec §10's end-to-end check, in full, now that paybacks exist.
     *
     * Thirteen people, two $1,000 bills fronted by Bob. Nine pay him $100 towards the deposit;
     * twelve pay $76.92 towards the balance. Then Jack turns up having paid nothing, and is ticked
     * onto both lists. Every figure below is the spec's, and the column must come to exactly zero.
     */
    @Test
    fun `the whole hotel scenario, ending with the column summing to zero`() {
        val bob = signedIn("Bob")
        val tripId = bob.createTrip("Hokkaido")
        val bobMember = bob.memberIdOf(tripId)
        val friends = (1..12).map { bob.addMember(tripId, "Friend $it") }
        val category = bob.builtInCategory(tripId, "stay")

        val deposit = bob
            .post(
                "/api/trips/$tripId/items",
                expense("Hotel deposit", thousandDollars, category, bobMember, listOf(bobMember) + friends),
            ).id()
        val balance = bob
            .post(
                "/api/trips/$tripId/items",
                expense("Hotel balance", thousandDollars, category, bobMember, listOf(bobMember) + friends),
            ).id()

        // Recorded by Bob, who is the person owed, so each is agreed on the spot — the spec's
        // "approve them all as Bob".
        friends.take(9).forEach { bob.payBack(deposit, it, 10_000) }
        friends.forEach { bob.payBack(balance, it, 7_692) }

        val jack = bob.addMember(tripId, "Jack")
        listOf(deposit, balance).forEach { itemId ->
            val fourteen = (listOf(bobMember) + friends + jack).map { mapOf("memberId" to it.toString()) }
            bob.patch("/api/items/$itemId", mapOf("sharedBy" to fourteen))
        }

        val invite = bob.invite(tripId)
        val nets = (friends + jack).associateWith { member ->
            signedIn("Member").let { client ->
                client.claim(tripId, invite, member)
                client.get("/api/trips/$tripId").json()["yourNetMinor"].asLong()
            }
        }
        val bobsNet = bob.get("/api/trips/$tripId").json()["yourNetMinor"].asLong()

        // The spec's figures. The tolerance is the one cent that largest remainder hands to some
        // people and not others across two bills — not slack for an arithmetic mistake.
        friends.take(9).forEach { assertNear(3_406, nets.getValue(it), "an original nine") }
        friends.drop(9).forEach { assertNear(-6_594, nets.getValue(it), "a stage-two joiner") }
        assertNear(-14_286, nets.getValue(jack), "Jack")
        assertNear(3_410, bobsNet, "Bob")

        // No tolerance here, ever. This is the invariant the app cannot survive breaking.
        assertEquals(0, bobsNet + nets.values.sum(), "the settle-up column does not sum to zero")
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun assertNear(expected: Long, actual: Long, who: String) =
        assertTrue(actual in (expected - 2)..(expected + 2), "$who should be about $expected but was $actual")

    private fun SessionAwareClient.payBack(itemId: UUID, fromMemberId: UUID, amountMinor: Long) {
        val response = post(
            "/api/items/$itemId/paybacks",
            mapOf(
                "fromMemberId" to fromMemberId.toString(),
                "amountMinor" to amountMinor,
                "paidOn" to "2026-08-01",
            ),
        )
        check(!response.statusCode.isError) { "could not record a payback: ${response.statusCode} ${response.body}" }
    }

    private fun SessionAwareClient.memberIdOf(tripId: UUID): UUID {
        val you = get("/api/trips/$tripId").json()["members"].first { it["isYou"].asBoolean() }
        return UUID.fromString(you["id"].asText())
    }

    private fun SessionAwareClient.items(tripId: UUID): List<JsonNode> =
        get("/api/trips/$tripId").json()["items"].toList()

    /** What one member owes across every bill on the trip. */
    private fun SessionAwareClient.totalShare(tripId: UUID, of: UUID): Long =
        items(tripId).sumOf { item ->
            item["splits"]
                .filter { it["memberId"].asText() == of.toString() }
                .sumOf { it["amountMinor"].asLong() }
        }

    /** Every share of every bill, which must equal every bill's total exactly. */
    private fun SessionAwareClient.totalOwedAcross(tripId: UUID): Long =
        items(tripId).sumOf { item -> item["splits"].sumOf { it["amountMinor"].asLong() } }
}
