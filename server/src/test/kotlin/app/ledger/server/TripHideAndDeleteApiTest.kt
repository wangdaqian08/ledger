package app.ledger.server

import app.ledger.server.trip.TripPurge
import app.ledger.server.trip.TripRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Putting a finished trip away, and getting rid of one (spec §3 · §5).
 *
 * The two are deliberately different acts and this test holds the difference: a hidden trip is off
 * the home list and otherwise entirely normal — it opens, it settles, its balance still counts —
 * while a deleted one is gone for everybody, restorable only by the person who deleted it, and
 * destroyed for good once the window passes.
 */
class TripHideAndDeleteApiTest : ApiTest() {
    @Autowired
    private lateinit var purge: TripPurge

    @Autowired
    private lateinit var trips: TripRepository

    private val nothing = emptyMap<String, String>()

    private fun SessionAwareClient.end(tripId: UUID) = post("/api/trips/$tripId/close", nothing)

    private fun SessionAwareClient.tripsList() = get("/api/trips").json()

    private fun SessionAwareClient.listed(tripId: UUID) =
        tripsList()["trips"].any { it["id"].asText() == tripId.toString() }

    /** The home screen's headline figure for one currency. */
    private fun SessionAwareClient.overall(currencyCode: String = "AUD"): Long =
        tripsList()["overalls"].single { it["currencyCode"].asText() == currencyCode }["netMinor"].asLong()

    @Test
    fun `the creator puts an ended trip away and takes it back out, and it stays a normal trip`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip("Snow Trip")
        creator.end(tripId)

        val hidden = creator.post("/api/trips/$tripId/hide", nothing)
        assertEquals(HttpStatus.OK, hidden.statusCode, "hiding failed: ${hidden.body}")
        assertTrue(hidden.json()["hiddenAt"].isTextual, "TripView should say when the trip was put away")
        assertTrue(
            creator.get("/api/trips/$tripId").json()["hiddenAt"].isTextual,
            "the hidden state must survive a reload",
        )

        // Hidden is a listing decision, not a withholding: the payload still carries the trip, so
        // its balance still counts and the screen can offer a way to reveal it.
        assertTrue(creator.listed(tripId), "a hidden trip stays in the payload for the screen to decide about")

        val shown = creator.post("/api/trips/$tripId/unhide", nothing)
        assertEquals(HttpStatus.OK, shown.statusCode, "unhiding failed: ${shown.body}")
        assertTrue(shown.json()["hiddenAt"].isNull, "unhiding clears the mark")
    }

    @Test
    fun `a live trip cannot be put away — end it first`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()

        val refused = creator.post("/api/trips/$tripId/hide", nothing)
        assertEquals(HttpStatus.CONFLICT, refused.statusCode, "hiding a trip nobody has ended")
        assertTrue(
            refused.json()["detail"].asText().contains("End this trip"),
            "the reason should point at ending it, got: ${refused.body}",
        )
        assertTrue(creator.get("/api/trips/$tripId").json()["hiddenAt"].isNull, "and nothing moved")
    }

    @Test
    fun `a hidden trip still settles — putting it away is tidying, not closing the books`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()
        val me = creator.yourMemberId(tripId)
        val bob = creator.addMember(tripId, "Bob")
        val food = creator.builtInCategory(tripId)
        creator.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me, bob))).id()
        creator.end(tripId)
        creator.post("/api/trips/$tripId/hide", nothing)

        val settlement = creator.post(
            "/api/trips/$tripId/settlements",
            mapOf("toMemberId" to bob.toString(), "amountMinor" to 200),
        )
        assertEquals(HttpStatus.CREATED, settlement.statusCode, "settling must outlive hiding: ${settlement.body}")
    }

    @Test
    fun `a hidden trip's balance still counts in the overall position`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()
        val me = creator.yourMemberId(tripId)
        val bob = creator.addMember(tripId, "Bob")
        val food = creator.builtInCategory(tripId)
        creator.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me, bob)))
        creator.end(tripId)

        val before = creator.overall()
        creator.post("/api/trips/$tripId/hide", nothing)
        val after = creator.overall()

        assertEquals(1500, before, "Bob owes half of a $30 dinner")
        assertEquals(before, after, "a hidden debt is still a debt — hiding must not quietly settle it")
    }

    @Test
    fun `only the creator may hide, unhide, delete or restore — a member gets 403, a stranger 404`() {
        val creator = signedIn("Nora")
        val friend = signedIn("Piet")
        val stranger = signedIn("Sam")
        val tripId = creator.createTrip()
        val seat = creator.addMember(tripId, "Piet")
        friend.claim(tripId, creator.invite(tripId), seat)
        creator.end(tripId)

        for (path in listOf("hide", "unhide", "restore")) {
            assertEquals(
                HttpStatus.FORBIDDEN,
                friend.post("/api/trips/$tripId/$path", nothing).statusCode,
                "a member on the trip attempting $path",
            )
            assertEquals(
                HttpStatus.NOT_FOUND,
                stranger.post("/api/trips/$tripId/$path", nothing).statusCode,
                "a stranger must not learn the trip exists by attempting $path",
            )
        }
        assertEquals(HttpStatus.FORBIDDEN, friend.delete("/api/trips/$tripId").statusCode, "a member deleting")
        assertEquals(HttpStatus.NOT_FOUND, stranger.delete("/api/trips/$tripId").statusCode, "a stranger deleting")
    }

    @Test
    fun `deleting takes the trip off everyone's list and out of reach, by API and by link`() {
        val creator = signedIn("Nora")
        val friend = signedIn("Piet")
        val tripId = creator.createTrip("Snow Trip")
        val seat = creator.addMember(tripId, "Piet")
        val token = creator.invite(tripId)
        friend.claim(tripId, token, seat)

        val deleted = creator.delete("/api/trips/$tripId")
        assertEquals(HttpStatus.NO_CONTENT, deleted.statusCode, "deleting failed: ${deleted.body}")

        assertFalse(creator.listed(tripId), "gone from the person who deleted it")
        assertFalse(friend.listed(tripId), "and from everybody else, at once")
        assertEquals(HttpStatus.NOT_FOUND, creator.get("/api/trips/$tripId").statusCode, "gone by API for its creator")
        assertEquals(HttpStatus.NOT_FOUND, friend.get("/api/trips/$tripId").statusCode, "and for its members")

        // A link handed out before the delete must not be a way back in.
        assertEquals(
            HttpStatus.NOT_FOUND,
            friend.post("/api/trips/$tripId/claimable", mapOf("token" to token)).statusCode,
            "the share link's landing page",
        )
        assertEquals(
            HttpStatus.NOT_FOUND,
            signedIn("Late").claim(tripId, token, seat).statusCode,
            "claiming a seat on a deleted trip",
        )
    }

    @Test
    fun `the creator sees what they deleted, with the date it stops being restorable, and nobody else does`() {
        val creator = signedIn("Nora")
        val friend = signedIn("Piet")
        val tripId = creator.createTrip("Snow Trip")
        val seat = creator.addMember(tripId, "Piet")
        friend.claim(tripId, creator.invite(tripId), seat)

        creator.delete("/api/trips/$tripId")

        val row = creator.tripsList()["deleted"].single { it["id"].asText() == tripId.toString() }
        assertEquals("Snow Trip", row["name"].asText(), "enough to recognise which trip it was")
        val deletedAt = Instant.parse(row["deletedAt"].asText())
        val purgesAt = Instant.parse(row["purgesAt"].asText())
        assertEquals(
            Duration.ofDays(30),
            Duration.between(deletedAt, purgesAt),
            "the section promises a deadline, so the server computes it rather than the client guessing",
        )

        assertTrue(
            friend.tripsList()["deleted"].isEmpty,
            "a member sees no bin of somebody else's deletions — for them the trip simply ended",
        )
    }

    @Test
    fun `restoring brings the whole trip back, for its members too`() {
        val creator = signedIn("Nora")
        val friend = signedIn("Piet")
        val tripId = creator.createTrip("Snow Trip")
        val seat = creator.addMember(tripId, "Piet")
        friend.claim(tripId, creator.invite(tripId), seat)
        val me = creator.yourMemberId(tripId)
        val food = creator.builtInCategory(tripId)
        creator.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me, seat)))

        creator.delete("/api/trips/$tripId")
        val restored = creator.post("/api/trips/$tripId/restore", nothing)

        assertEquals(HttpStatus.OK, restored.statusCode, "restoring failed: ${restored.body}")
        assertEquals("Snow Trip", restored.json()["name"].asText(), "the same trip, not a husk of one")
        assertEquals(2, restored.json()["members"].size(), "with its roster")
        assertEquals(1, restored.json()["items"].size(), "the expenses came back with it")
        assertEquals(1500, restored.json()["yourNetMinor"].asLong(), "and so did the balance")
        assertTrue(creator.listed(tripId), "back on its creator's list")
        assertTrue(friend.listed(tripId), "and back on everyone else's")
        assertTrue(creator.tripsList()["deleted"].isEmpty, "and out of the bin")
    }

    @Test
    fun `restoring a trip nobody deleted is a conflict, and a member cannot undo a delete`() {
        val creator = signedIn("Nora")
        val friend = signedIn("Piet")
        val tripId = creator.createTrip()
        val seat = creator.addMember(tripId, "Piet")
        friend.claim(tripId, creator.invite(tripId), seat)

        assertEquals(
            HttpStatus.CONFLICT,
            creator.post("/api/trips/$tripId/restore", nothing).statusCode,
            "restoring a trip that is not deleted",
        )

        creator.delete("/api/trips/$tripId")
        assertEquals(
            HttpStatus.NOT_FOUND,
            friend.post("/api/trips/$tripId/restore", nothing).statusCode,
            "a deleted trip is gone for a member — including as something to argue with",
        )
    }

    @Test
    fun `the sweep destroys trips past the window and leaves the rest alone`() {
        val creator = signedIn("Nora")
        val doomed = creator.createTrip("Long gone")
        val recent = creator.createTrip("Just deleted")
        val alive = creator.createTrip("Still here")
        creator.delete("/api/trips/$doomed")
        creator.delete("/api/trips/$recent")

        // Backdating beats waiting thirty days; the sweep reads the clock, not the calendar.
        val old = trips.findById(doomed).orElseThrow()
        old.deletedAt = Instant.now().minus(Duration.ofDays(31))
        trips.save(old)

        assertEquals(1, purge.purgeDeleted(), "only the one past its window")

        assertTrue(trips.findById(doomed).isEmpty, "purged for good — the row is gone, and the cascade with it")
        assertNotNull(trips.findById(recent).orElse(null), "day zero is not day thirty")
        assertNotNull(trips.findById(alive).orElse(null), "a trip nobody deleted is not the sweep's business")
        assertNull(
            creator.tripsList()["deleted"].firstOrNull { it["id"].asText() == doomed.toString() },
            "and it leaves the bin it can no longer be restored from",
        )
    }
}
