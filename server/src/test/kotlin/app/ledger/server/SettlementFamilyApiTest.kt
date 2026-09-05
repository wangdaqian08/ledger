package app.ledger.server

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

/**
 * Families on the Settle-up screen (§7b): a trip member partitions the whole trip into Families,
 * and `POST /api/trips/{id}/families` hands back every Family's own net plus its bilateral
 * position with every *other* Family — never with an individual, never a minimised transfer set.
 *
 * Ephemeral by design: nothing here is persisted, so every case builds its own partition on the
 * fly and nothing is left behind for the next call to trip over.
 */
class SettlementFamilyApiTest : ApiTest() {
    @Test
    fun `nothing built, every member is their own family`() {
        val trip = fiveWayTrip()

        val families = trip.alice.families(trip.id).json()["families"]

        assertEquals(5, families.size())
        assertEquals(
            setOf(trip.aliceMember, trip.bobMember, trip.cathyMember, trip.danaMember, trip.erinMember)
                .map { setOf(it) }
                .toSet(),
            families.map { it.memberIds() }.toSet(),
        )
    }

    @Test
    fun `the worked example - two explicit families, one auto-singleton`() {
        val trip = fiveWayTrip()

        val families = trip.alice
            .families(
                trip.id,
                listOf(trip.aliceMember, trip.bobMember),
                listOf(trip.cathyMember, trip.danaMember),
            ).json()["families"]

        assertEquals(3, families.size())
        assertEquals(
            setOf(
                setOf(trip.aliceMember, trip.bobMember),
                setOf(trip.cathyMember, trip.danaMember),
                setOf(trip.erinMember),
            ),
            families.map { it.memberIds() }.toSet(),
        )
    }

    @Test
    fun `an explicit family built from the last two roster members still comes first in the response`() {
        val trip = fiveWayTrip()

        val families = trip.alice
            .families(trip.id, listOf(trip.danaMember, trip.erinMember))
            .json()["families"]

        // Dana and Erin are the last two members added to the trip; a response that fell back to
        // roster order instead of build order would put this family last, not first. Asserting on
        // the literal array — index 0, never a set — is what tells the two apart.
        assertEquals(setOf(trip.danaMember, trip.erinMember), families[0].memberIds())
        assertEquals(
            listOf(
                setOf(trip.danaMember, trip.erinMember),
                setOf(trip.aliceMember),
                setOf(trip.bobMember),
                setOf(trip.cathyMember),
            ),
            families.map { it.memberIds() },
        )
    }

    @Test
    fun `a family's card has one row per other family, not per other person`() {
        val trip = fiveWayTrip()

        val families = trip.alice
            .families(
                trip.id,
                listOf(trip.aliceMember, trip.bobMember),
                listOf(trip.cathyMember, trip.danaMember),
            ).json()["families"]

        // 3 families total ({alice,bob}, {cathy,dana}, {erin}) -> 2 *other families* on every
        // card, never 5 - 2 = 3, which is what per-individual counterparts would give {alice,bob}.
        assertEquals(3, families.size())
        families.forEach { family ->
            assertEquals(2, family["counterparts"].size(), "${family.memberIds()} counterpart count")
        }
    }

    @Test
    fun `a family-vs-family figure is the raw sum of the per-person settlement rows`() {
        val trip = fiveWayTrip()

        val families = trip.alice
            .families(
                trip.id,
                listOf(trip.aliceMember, trip.bobMember),
                listOf(trip.cathyMember, trip.danaMember),
            ).json()["families"]
        val ab = families.first { it.memberIds() == setOf(trip.aliceMember, trip.bobMember) }
        val cd = ab["counterparts"].first { it.memberIds() == setOf(trip.cathyMember, trip.danaMember) }

        // The same figure, built entirely by hand from GET /settlement's existing per-person rows:
        // alice's row for cathy and dana, plus bob's row for cathy and dana. If the endpoint ever
        // started reading from settle()'s minimised transfers instead of raw bilateral sums, this
        // would catch it — bob's net is not zero here, but the point generalises regardless.
        val aliceRows = trip.alice.rows(trip.id)
        val bobRows = trip.bob.rows(trip.id)
        val expected = aliceRows.getValue(trip.cathyMember) + aliceRows.getValue(trip.danaMember) +
            bobRows.getValue(trip.cathyMember) + bobRows.getValue(trip.danaMember)

        assertEquals(expected, cd["owedMinor"].asLong())
    }

    @Test
    fun `every family's net sums to zero across the partition`() {
        val trip = fiveWayTrip()

        val families = trip.alice
            .families(
                trip.id,
                listOf(trip.aliceMember, trip.bobMember),
                listOf(trip.cathyMember, trip.danaMember),
            ).json()["families"]

        assertEquals(0L, families.sumOf { it["netMinor"].asLong() })
    }

    @Test
    fun `a 1-person explicit family is allowed, and is identical to leaving them out`() {
        val trip = fiveWayTrip()

        val explicit = trip.alice.families(trip.id, listOf(trip.aliceMember)).json()
        val implicit = trip.alice.families(trip.id).json()

        assertEquals(implicit, explicit)
    }

    @Test
    fun `two families covering everyone leaves no auto-singletons`() {
        val trip = fiveWayTrip()

        val families = trip.alice
            .families(
                trip.id,
                listOf(trip.aliceMember, trip.bobMember),
                listOf(trip.cathyMember, trip.danaMember, trip.erinMember),
            ).json()["families"]

        assertEquals(2, families.size())
    }

    @Test
    fun `an empty explicit family is refused`() {
        val trip = fiveWayTrip()

        val response = trip.alice.post(
            "/api/trips/${trip.id}/families",
            mapOf("families" to listOf(mapOf("memberIds" to emptyList<String>()))),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `the same member listed twice in one family is refused`() {
        val trip = fiveWayTrip()

        val response = trip.alice.families(trip.id, listOf(trip.aliceMember, trip.aliceMember))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `two families sharing a member are refused`() {
        val trip = fiveWayTrip()

        val response = trip.alice.families(
            trip.id,
            listOf(trip.aliceMember, trip.bobMember),
            listOf(trip.bobMember, trip.cathyMember),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a member not on the trip is refused`() {
        val trip = fiveWayTrip()
        val elsewhere = fiveWayTrip()

        val response = trip.alice.families(trip.id, listOf(trip.aliceMember, elsewhere.bobMember))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a single family covering the whole trip is refused`() {
        val trip = fiveWayTrip()

        val response = trip.alice.families(
            trip.id,
            listOf(trip.aliceMember, trip.bobMember, trip.cathyMember, trip.danaMember, trip.erinMember),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a stranger gets 404, not 403`() {
        val trip = fiveWayTrip()

        val response = signedIn("Stranger").post(
            "/api/trips/${trip.id}/families",
            mapOf("families" to emptyList<Any>()),
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `two calls with different selections don't remember each other`() {
        val trip = fiveWayTrip()

        trip.alice.families(trip.id, listOf(trip.aliceMember, trip.bobMember))
        val second = trip.alice.families(trip.id, listOf(trip.cathyMember, trip.danaMember)).json()["families"]

        // Alice and Bob must be back to singletons here — the first call's grouping left no trace.
        // {cathy,dana} plus 3 singletons (alice, bob, erin) is 4 families, not the 3 a lingering
        // {alice,bob} grouping would produce.
        assertEquals(4, second.size())
        assertTrue(second.any { it.memberIds() == setOf(trip.aliceMember) })
        assertTrue(second.any { it.memberIds() == setOf(trip.bobMember) })
        assertTrue(second.any { it.memberIds() == setOf(trip.cathyMember, trip.danaMember) })
    }

    // --- helpers -----------------------------------------------------------------------------

    private class Fixture(
        val alice: SessionAwareClient,
        val bob: SessionAwareClient,
        val cathy: SessionAwareClient,
        val dana: SessionAwareClient,
        val erin: SessionAwareClient,
        val id: UUID,
        val aliceMember: UUID,
        val bobMember: UUID,
        val cathyMember: UUID,
        val danaMember: UUID,
        val erinMember: UUID,
    )

    /**
     * Alice fronts a $100 dinner for all five ($20 each), Bob fronts a $20 taxi with Cathy ($10
     * each), and Dana fronts $20 of groceries with Erin ($10 each) — enough cross-pair debt that a
     * Family-vs-Family figure is never reducible to one person's row.
     */
    private fun fiveWayTrip(): Fixture {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val aliceMember = alice.yourMemberId(tripId)
        val bobMember = alice.addMember(tripId, "Bob")
        val cathyMember = alice.addMember(tripId, "Cathy")
        val danaMember = alice.addMember(tripId, "Dana")
        val erinMember = alice.addMember(tripId, "Erin")
        val invite = alice.invite(tripId)
        val bob = signedIn("Bob").also { it.claim(tripId, invite, bobMember) }
        val cathy = signedIn("Cathy").also { it.claim(tripId, invite, cathyMember) }
        val dana = signedIn("Dana").also { it.claim(tripId, invite, danaMember) }
        val erin = signedIn("Erin").also { it.claim(tripId, invite, erinMember) }
        val food = alice.builtInCategory(tripId)

        alice.post(
            "/api/trips/$tripId/items",
            expense(
                "Dinner",
                10_000,
                food,
                aliceMember,
                listOf(aliceMember, bobMember, cathyMember, danaMember, erinMember),
            ),
        )
        bob.post(
            "/api/trips/$tripId/items",
            expense("Taxi", 2_000, food, bobMember, listOf(bobMember, cathyMember)),
        )
        dana.post(
            "/api/trips/$tripId/items",
            expense("Groceries", 2_000, food, danaMember, listOf(danaMember, erinMember)),
        )

        return Fixture(
            alice,
            bob,
            cathy,
            dana,
            erin,
            tripId,
            aliceMember,
            bobMember,
            cathyMember,
            danaMember,
            erinMember,
        )
    }

    private fun SessionAwareClient.families(tripId: UUID, vararg explicit: List<UUID>) =
        post(
            "/api/trips/$tripId/families",
            mapOf("families" to explicit.map { family -> mapOf("memberIds" to family.map { it.toString() }) }),
        )

    private fun SessionAwareClient.rows(tripId: UUID): Map<UUID, Long> =
        get("/api/trips/$tripId/settlement")
            .json()["rows"]
            .associate { UUID.fromString(it["memberId"].asText()) to it["owedMinor"].asLong() }

    private fun JsonNode.memberIds(): Set<UUID> = this["members"].map { UUID.fromString(it["id"].asText()) }.toSet()
}
