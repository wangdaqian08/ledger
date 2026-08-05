package app.ledger.server

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Trips, members and the claim flow — build order step 4, whose stated deliverable is the
 * permission tests as much as the endpoints.
 *
 * Everything here goes over real HTTP as a real signed-in person, because the permission rules are
 * about *who is asking*, and a test that calls the service directly has already skipped that.
 */
class TripApiTest : ApiTest() {
    // --- creating and seeing trips -------------------------------------------------------------

    @Test
    fun `creating a trip makes you its first member, already claimed`() {
        val alice = signedIn("Alice")

        val response = alice.post("/api/trips", newTrip("Hokkaido"))

        assertEquals(HttpStatus.CREATED, response.statusCode)
        val members = response.json()["members"]
        assertEquals(1, members.size())
        // Signed-in names carry a per-test suffix so one Postgres can serve the whole suite; the
        // point here is that the slot is named after the creator, not the exact string.
        assertTrue(members[0]["displayName"].asText().startsWith("Alice"))
        assertTrue(members[0]["isYou"].asBoolean())
        assertTrue(members[0]["claimed"].asBoolean(), "the creator's own slot should not need claiming")
    }

    @Test
    fun `a trip you are not on is indistinguishable from one that does not exist`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Private")
        val stranger = signedIn("Stranger")

        // 404 rather than 403: a stranger should not be able to confirm the trip is even real.
        assertEquals(HttpStatus.NOT_FOUND, stranger.get("/api/trips/$tripId").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, stranger.get("/api/trips/${UUID.randomUUID()}").statusCode)
    }

    @Test
    fun `listing trips returns yours and nobody else's`() {
        val alice = signedIn("Alice")
        val bob = signedIn("Bob")
        alice.post("/api/trips", newTrip("Alice's trip"))
        bob.post("/api/trips", newTrip("Bob's trip"))

        val listed = alice.get("/api/trips").json()["trips"]

        assertEquals(1, listed.size())
        assertEquals("Alice's trip", listed[0]["name"].asText())
    }

    @Test
    fun `signing out closes the door`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        alice.delete("/api/auth/session")

        assertEquals(HttpStatus.UNAUTHORIZED, alice.get("/api/trips/$tripId").statusCode)
    }

    @Test
    fun `an unknown currency is refused before a trip is created`() {
        val alice = signedIn("Alice")

        val response = alice.post("/api/trips", newTrip("Nowhere") + mapOf("currencyCode" to "XYZ"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(0, alice.get("/api/trips").json()["trips"].size(), "a rejected trip was still created")
    }

    // --- the roster ----------------------------------------------------------------------------

    @Test
    fun `only the creator can add members`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        val bobMember = alice.addMember(tripId, "Bob")
        val bob = signedIn("Bob")
        bob.claim(tripId, alice.invite(tripId), bobMember)

        // Bob is a fully paid-up member of this trip and still cannot change its roster.
        val asMember = bob.post("/api/trips/$tripId/members", mapOf("displayName" to "Carol"))

        assertEquals(HttpStatus.FORBIDDEN, asMember.statusCode)
        assertEquals(HttpStatus.OK, bob.get("/api/trips/$tripId").statusCode, "but he can still see the trip")
    }

    @Test
    fun `person hues go round the eight and start again`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Big group")

        // Alice already holds hue 1, so nine more members take us past the end of the ramp.
        repeat(9) { alice.post("/api/trips/$tripId/members", mapOf("displayName" to "Friend $it")) }

        val hues = alice.get("/api/trips/$tripId").json()["members"].map { it["personHue"].asInt() }
        assertEquals((1..8).toList() + listOf(1, 2), hues)
    }

    @Test
    fun `two people on one trip cannot share a name, whatever the capitalisation`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        alice.post("/api/trips/$tripId/members", mapOf("displayName" to "Bob"))

        // Picking the right name is how a friend claims their slot; two Bobs makes that a coin toss.
        val duplicate = alice.post("/api/trips/$tripId/members", mapOf("displayName" to "  bob "))

        assertEquals(HttpStatus.CONFLICT, duplicate.statusCode)
    }

    // --- the claim flow ------------------------------------------------------------------------

    @Test
    fun `a friend follows the link, picks their name, and is on the trip`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        val bobMember = alice.addMember(tripId, "Bob")
        val bob = signedIn("Bob")
        assertEquals(HttpStatus.NOT_FOUND, bob.get("/api/trips/$tripId").statusCode)

        val claimed = bob.claim(tripId, alice.invite(tripId), bobMember)

        assertEquals(HttpStatus.OK, claimed.statusCode)
        val bobsRow = claimed.json()["members"].first { it["id"].asText() == bobMember.toString() }
        assertTrue(bobsRow["isYou"].asBoolean())
        assertTrue(bobsRow["claimed"].asBoolean())
        assertEquals(1, bob.get("/api/trips").json()["trips"].size(), "the trip did not appear in Bob's list")
    }

    @Test
    fun `a name somebody else has already claimed cannot be taken`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        val bobMember = alice.addMember(tripId, "Bob")
        val invite = alice.invite(tripId)
        signedIn("Bob").claim(tripId, invite, bobMember)

        // The link is shareable, so a second person plausibly arrives holding the same one.
        val impostor = signedIn("Impostor").claim(tripId, invite, bobMember)

        assertEquals(HttpStatus.CONFLICT, impostor.statusCode)
    }

    @Test
    fun `one person cannot hold two slots on the same trip`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        val bobMember = alice.addMember(tripId, "Bob")
        val spare = alice.addMember(tripId, "Spare")
        val invite = alice.invite(tripId)
        val bob = signedIn("Bob")
        bob.claim(tripId, invite, bobMember)

        // Two slots would let one person owe and be owed as two people. The balances would still
        // sum to zero and still be nonsense.
        assertEquals(HttpStatus.CONFLICT, bob.claim(tripId, invite, spare).statusCode)
    }

    @Test
    fun `two devices racing the same claim cannot leave one person holding two slots`() {
        // The test above proves the sequential check; this one proves the race. Both requests can
        // pass the "not on this trip yet" read together, so it is the database's unique index —
        // not the service's politeness — that has to hold the line.
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        val first = alice.addMember(tripId, "Bob")
        val second = alice.addMember(tripId, "Bobby")
        val invite = alice.invite(tripId)
        val bob = signedIn("Bob")

        val together = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        val outcomes = try {
            listOf(first, second)
                .map { member ->
                    executor.submit<HttpStatus> {
                        together.await()
                        bob.claim(tripId, invite, member).statusCode as HttpStatus
                    }
                }.map { it.get() }
        } finally {
            executor.shutdown()
        }

        assertEquals(1, outcomes.count { it == HttpStatus.OK }, "exactly one claim wins: $outcomes")
        assertEquals(1, outcomes.count { it == HttpStatus.CONFLICT }, "and the other is told no: $outcomes")
    }

    @Test
    fun `a link for one trip does not open another`() {
        val alice = signedIn("Alice")
        val hokkaido = alice.createTrip("Hokkaido")
        val kyoto = alice.createTrip("Kyoto")
        val kyotoMember = alice.addMember(kyoto, "Bob")

        val crossed = signedIn("Bob").claim(kyoto, alice.invite(hokkaido), kyotoMember)

        assertEquals(HttpStatus.BAD_REQUEST, crossed.statusCode)
    }

    @Test
    fun `a tampered link is refused`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        val bobMember = alice.addMember(tripId, "Bob")
        val invite = alice.invite(tripId)

        val forged = signedIn("Mallory").claim(tripId, invite.dropLast(4) + "aaaa", bobMember)

        assertEquals(HttpStatus.BAD_REQUEST, forged.statusCode)
    }

    @Test
    fun `a member of another trip cannot be claimed through this trip's link`() {
        val alice = signedIn("Alice")
        val hokkaido = alice.createTrip("Hokkaido")
        val kyoto = alice.createTrip("Kyoto")
        val kyotoMember = alice.addMember(kyoto, "Bob")

        val response = signedIn("Bob").claim(hokkaido, alice.invite(hokkaido), kyotoMember)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `only the creator can hand out a link`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        val bobMember = alice.addMember(tripId, "Bob")
        val bob = signedIn("Bob")
        bob.claim(tripId, alice.invite(tripId), bobMember)

        assertEquals(HttpStatus.FORBIDDEN, bob.post("/api/trips/$tripId/invite", emptyMap<String, String>()).statusCode)
    }

    // --- balances ------------------------------------------------------------------------------

    @Test
    fun `a trip with no spending leaves everybody square`() {
        // Zero today because items arrive at step 5 — but the number comes from the engine, not
        // from a literal, so this is the seam being exercised rather than a placeholder.
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        alice.post("/api/trips/$tripId/members", mapOf("displayName" to "Bob"))

        val trips = alice.get("/api/trips").json()

        assertEquals(0, trips["trips"][0]["yourNetMinor"].asLong())
        assertEquals(0, trips["overallNetMinor"].asLong())
        assertEquals(1, trips["settledTripCount"].asInt())
    }
}
