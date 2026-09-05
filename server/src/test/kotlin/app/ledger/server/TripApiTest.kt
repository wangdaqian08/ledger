package app.ledger.server

import java.util.*
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

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
        // A 403 must still say why. Answering with a bare status the client cannot explain is the
        // legacy error body problemdetails exists to replace — it just never covered access denials.
        assertTrue(
            asMember.body!!.contains("Only the person who created this trip"),
            "the 403 threw away its reason: ${asMember.body}",
        )
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

    @Test
    fun `the creator can fix a typo'd name, and everybody sees the correction`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        val memberId = alice.addMember(tripId, "Bobb")

        val renamed = alice.patch("/api/trips/$tripId/members/$memberId", mapOf("displayName" to " Bob "))

        assertEquals(HttpStatus.OK, renamed.statusCode)
        assertEquals("Bob", renamed.json()["displayName"].asText())
        val roster = alice.get("/api/trips/$tripId").json()["members"].map { it["displayName"].asText() }
        assertTrue("Bob" in roster, "the fixed name is on the roster: $roster")
        assertFalse("Bobb" in roster, "the typo is gone from the roster: $roster")
    }

    @Test
    fun `a name fix cannot land on somebody else's name, but re-casing your own is no collision`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        alice.addMember(tripId, "Bob")
        val caraId = alice.addMember(tripId, "cara")

        val stolen = alice.patch("/api/trips/$tripId/members/$caraId", mapOf("displayName" to "BOB"))
        assertEquals(HttpStatus.CONFLICT, stolen.statusCode)

        // "cara" → "Cara" collides only with itself, which is exactly what a typo fix looks like.
        val recased = alice.patch("/api/trips/$tripId/members/$caraId", mapOf("displayName" to "Cara"))
        assertEquals(HttpStatus.OK, recased.statusCode)
        assertEquals("Cara", recased.json()["displayName"].asText())
    }

    @Test
    fun `renaming follows the roster rule — creator only, and strangers see nothing`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        val bobMember = alice.addMember(tripId, "Bob")
        val bob = signedIn("Bob")
        bob.claim(tripId, alice.invite(tripId), bobMember)

        // Even the person the seat belongs to: the roster is the creator's (spec §5).
        val ownSeat = bob.patch("/api/trips/$tripId/members/$bobMember", mapOf("displayName" to "Bobby"))
        assertEquals(HttpStatus.FORBIDDEN, ownSeat.statusCode)

        val stranger = signedIn("Mallory").patch("/api/trips/$tripId/members/$bobMember", mapOf("displayName" to "X"))
        assertEquals(HttpStatus.NOT_FOUND, stranger.statusCode)
    }

    @Test
    fun `a member of another trip cannot be renamed through this trip`() {
        val alice = signedIn("Alice")
        val hokkaido = alice.createTrip("Hokkaido")
        val kyoto = alice.createTrip("Kyoto")
        val kyotoMember = alice.addMember(kyoto, "Bob")

        val crossed = alice.patch("/api/trips/$hokkaido/members/$kyotoMember", mapOf("displayName" to "Ben"))

        assertEquals(HttpStatus.NOT_FOUND, crossed.statusCode)
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
    fun `two different people racing the same seat cannot both claim it`() {
        // The test above proves one person cannot take two seats — the unique index holds that.
        // This is the other direction it cannot see: two people overwriting one seat's owner. Both
        // requests pass the "is it claimed?" read together, so the member row's version is what has
        // to hold the line, or the second writer silently steals the seat the first was told they
        // got.
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        val seat = alice.addMember(tripId, "Bob")
        val invite = alice.invite(tripId)
        val bob = signedIn("Bob")
        val carol = signedIn("Carol")

        val together = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        val outcomes = try {
            listOf(bob, carol)
                .map { claimant ->
                    executor.submit<HttpStatus> {
                        together.await()
                        claimant.claim(tripId, invite, seat).statusCode as HttpStatus
                    }
                }.map { it.get() }
        } finally {
            executor.shutdown()
        }

        assertEquals(1, outcomes.count { it == HttpStatus.OK }, "exactly one person gets the seat: $outcomes")
        assertEquals(1, outcomes.count { it == HttpStatus.CONFLICT }, "and the other is turned away: $outcomes")

        // The trip agrees: the seat has one owner, the winner — not whoever happened to write last.
        val members = alice.get("/api/trips/$tripId").json()["members"]
        assertEquals(
            1,
            members.count { it["id"].asText() == seat.toString() && it["claimed"].asBoolean() },
            "the seat is claimed once, by the winner",
        )
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
    fun `a link's landing page shows the names still free, and nothing else`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        alice.addMember(tripId, "Bob")
        val invite = alice.invite(tripId)
        val friend = signedIn("Friend")

        val preview = friend.post("/api/trips/$tripId/claimable", mapOf("token" to invite))

        assertEquals(HttpStatus.OK, preview.statusCode)
        val body = preview.json()
        assertEquals("Hokkaido", body["tripName"].asText())
        // Alice claimed her slot by creating the trip, so only Bob's name is on offer — and the
        // payload carries no items, no balances and no claimed members for a stranger to read.
        assertEquals(listOf("Bob"), body["members"].map { it["displayName"].asText() })
        assertFalse(body.has("items"), "a link preview must not carry the trip's numbers")

        val tampered = friend.post("/api/trips/$tripId/claimable", mapOf("token" to invite.dropLast(4) + "aaaa"))
        assertEquals(HttpStatus.BAD_REQUEST, tampered.statusCode)
    }

    @Test
    fun `the landing page tells somebody already aboard which name is theirs`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        alice.addMember(tripId, "Bob")
        val invite = alice.invite(tripId)

        // Creating the trip claimed Alice's own slot, so the link must say so rather than offer
        // her names that can only be refused — every claim by a member is a guaranteed 409.
        val aboard = alice.post("/api/trips/$tripId/claimable", mapOf("token" to invite)).json()
        assertTrue(aboard["you"]["displayName"].asText().startsWith("Alice"))

        // Somebody holding no slot gets the free names and an explicit nothing for `you` —
        // still without learning who the claimed names belong to.
        val fresh = signedIn("Friend").post("/api/trips/$tripId/claimable", mapOf("token" to invite)).json()
        assertTrue(fresh["you"].isNull, "a stranger has no seat to be told about")
        assertEquals(listOf("Bob"), fresh["members"].map { it["displayName"].asText() })
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
        val overalls = trips["overalls"]
        assertEquals(1, overalls.size(), "one line per currency held")
        assertEquals("AUD", overalls[0]["currencyCode"].asText())
        assertEquals(0, overalls[0]["netMinor"].asLong())
        assertEquals(1, trips["settledTripCount"].asInt())
    }

    @Test
    fun `the overall figure sums each currency on its own, never across them`() {
        val alice = signedIn("Alice")

        // Two AUD trips, alice owed $1.00 on each — the dollars line must be the SUM, $2.00.
        repeat(2) { n ->
            val t = alice.createTrip("Aud $n")
            val me = alice.meOn(t)
            val friend = alice.addMember(t, "Friend $n")
            alice.post("/api/trips/$t/items", expense("Coffee", 200, alice.builtInCategory(t), me, listOf(me, friend)))
        }
        // One JPY trip, alice owed ¥3 — a yen figure has no business being folded into dollars.
        val jpy = alice.post("/api/trips", newTrip("Tokyo") + mapOf("currencyCode" to "JPY")).id()
        val meJ = alice.meOn(jpy)
        val friendJ = alice.addMember(jpy, "Yuki")
        alice.post("/api/trips/$jpy/items", expense("Sushi", 6, alice.builtInCategory(jpy), meJ, listOf(meJ, friendJ)))

        val overalls = alice
            .get("/api/trips")
            .json()["overalls"]
            .associate { it["currencyCode"].asText() to it["netMinor"].asLong() }

        assertEquals(200, overalls["AUD"], "two AUD trips of +100 each, summed")
        assertEquals(3, overalls["JPY"], "the yen trip stands on its own")
    }

    @Test
    fun `the trip payload says whether you are its creator`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        val bobMember = alice.addMember(tripId, "Bob")
        val bob = signedIn("Bob")
        bob.claim(tripId, alice.invite(tripId), bobMember)

        assertTrue(alice.get("/api/trips/$tripId").json()["youAreCreator"].asBoolean(), "the creator should be told so")
        assertFalse(
            bob.get("/api/trips/$tripId").json()["youAreCreator"].asBoolean(),
            "a plain member is not the creator",
        )
    }

    @Test
    fun `the trip payload carries the three headline figures, derived by the engine`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hokkaido")
        val me = alice.meOn(tripId)
        val bob = alice.addMember(tripId, "Bob")
        // Alice fronts $100 split two ways: the group spent $100, her share is $50, she fronted $100.
        alice.post(
            "/api/trips/$tripId/items",
            expense("Hotel", 10_000, alice.builtInCategory(tripId), me, listOf(me, bob)),
        )

        val trip = alice.get("/api/trips/$tripId").json()

        assertEquals(10_000, trip["groupSpendMinor"].asLong(), "group spend")
        assertEquals(5_000, trip["yourShareMinor"].asLong(), "your share")
        assertEquals(10_000, trip["youFrontedMinor"].asLong(), "you fronted")
    }

    private fun SessionAwareClient.meOn(tripId: UUID): UUID =
        UUID.fromString(
            get("/api/trips/$tripId").json()["members"].first { it["isYou"].asBoolean() }["id"].asText(),
        )
}
