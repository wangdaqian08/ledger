package app.ledger.server

import app.ledger.server.receipt.ReceiptRepository
import app.ledger.server.receipt.ReceiptStorage
import app.ledger.server.trip.TripPurge
import app.ledger.server.trip.TripRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus

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

    @Autowired
    private lateinit var receiptRows: ReceiptRepository

    @Autowired
    private lateinit var receiptObjects: ReceiptStorage

    private val nothing = emptyMap<String, String>()

    private val jpeg = byteArrayOf(-1, -40, -1, -32) + ByteArray(120) { it.toByte() }

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
    fun `reopening a put-away trip takes it back out of the cupboard too`() {
        // Hidden means "finished and put away", and the CHECK constraint enforces exactly that
        // pairing — so reopening has to clear the hidden mark, or the UPDATE itself is refused
        // and the creator gets a 500 for pressing a button the screen offered them.
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()
        creator.end(tripId)
        creator.post("/api/trips/$tripId/hide", nothing)

        val reopened = creator.post("/api/trips/$tripId/reopen", nothing)
        assertEquals(HttpStatus.OK, reopened.statusCode, "reopening a hidden trip: ${reopened.body}")
        assertTrue(reopened.json()["closedAt"].isNull, "open again")
        assertTrue(reopened.json()["hiddenAt"].isNull, "a live trip cannot stay put away")
        assertTrue(creator.listed(tripId), "and it is back on the home list, because live trips always are")
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
        assertTrue(
            creator.tripsList()["overalls"].isEmpty,
            "a deleted trip's money leaves the overall position with it",
        )
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
    fun `delete and restore repeat safely — a retry can never read as failure`() {
        val creator = signedIn("Nora")
        val friend = signedIn("Piet")
        val tripId = creator.createTrip()
        val seat = creator.addMember(tripId, "Piet")
        friend.claim(tripId, creator.invite(tripId), seat)

        // Restoring what was never deleted answers with the trip, not an argument — to a client
        // retrying a lost response, a 409 is indistinguishable from failure.
        val alreadyHere = creator.post("/api/trips/$tripId/restore", nothing)
        assertEquals(HttpStatus.OK, alreadyHere.statusCode)
        assertEquals(tripId, alreadyHere.id())
        assertEquals(HttpStatus.FORBIDDEN, friend.post("/api/trips/$tripId/restore", nothing).statusCode)

        creator.delete("/api/trips/$tripId")
        val stamp = creator.tripsList()["deleted"].single()["purgesAt"].asText()

        // The item-POST replay rule, applied to destruction: same answer again, and the purge
        // clock does not move, so a retry cannot quietly stretch the restore window.
        assertEquals(HttpStatus.NO_CONTENT, creator.delete("/api/trips/$tripId").statusCode)
        assertEquals(stamp, creator.tripsList()["deleted"].single()["purgesAt"].asText())

        assertEquals(
            HttpStatus.NOT_FOUND,
            friend.post("/api/trips/$tripId/restore", nothing).statusCode,
            "a deleted trip is gone for a member — including as something to argue with",
        )
        assertEquals(HttpStatus.OK, creator.post("/api/trips/$tripId/restore", nothing).statusCode)
        assertEquals(
            HttpStatus.OK,
            creator.post("/api/trips/$tripId/restore", nothing).statusCode,
            "and the retry of the restore agrees with the restore",
        )
    }

    @Test
    fun `a hidden trip that is deleted and restored comes back exactly as it was — ended, and still put away`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()
        creator.end(tripId)
        creator.post("/api/trips/$tripId/hide", nothing)

        creator.delete("/api/trips/$tripId")
        val restored = creator.post("/api/trips/$tripId/restore", nothing)

        assertEquals(HttpStatus.OK, restored.statusCode, "restoring a hidden trip: ${restored.body}")
        assertTrue(restored.json()["closedAt"].isTextual, "still ended")
        assertTrue(restored.json()["hiddenAt"].isTextual, "still put away — restore undoes the delete, nothing else")
    }

    @Test
    fun `a member sees the put-away trip too — hidden is a shared fact, not the creator's secret`() {
        val creator = signedIn("Nora")
        val friend = signedIn("Piet")
        val tripId = creator.createTrip()
        val seat = creator.addMember(tripId, "Piet")
        friend.claim(tripId, creator.invite(tripId), seat)
        val me = creator.yourMemberId(tripId)
        val food = creator.builtInCategory(tripId)
        creator.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me, seat)))
        creator.end(tripId)
        creator.post("/api/trips/$tripId/hide", nothing)

        val friendsTrip = friend.tripsList()["trips"].single { it["id"].asText() == tripId.toString() }
        assertTrue(friendsTrip["hiddenAt"].isTextual, "the member's payload carries the mark, so their screen files it")
        assertEquals(-1500, friend.overall(), "and the member's overall position still counts the hidden debt")
    }

    @Test
    fun `the trip names how much the group still has open — the figure the delete dialog reads`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()
        val me = creator.yourMemberId(tripId)
        val bob = creator.addMember(tripId, "Bob")
        val food = creator.builtInCategory(tripId)
        val itemId = creator.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me, bob))).id()

        assertEquals(1500, creator.get("/api/trips/$tripId").json()["unsettledMinor"].asLong(), "Bob's half is open")

        creator.post(
            "/api/items/$itemId/paybacks",
            mapOf("fromMemberId" to bob.toString(), "amountMinor" to 1500, "paidOn" to "2026-08-18"),
        )
        assertEquals(0, creator.get("/api/trips/$tripId").json()["unsettledMinor"].asLong(), "square is zero, exactly")
    }

    @Test
    fun `the schema itself refuses a hidden live trip, whatever some future code path forgets`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()

        val trip = trips.findById(tripId).orElseThrow()
        trip.hiddenAt = Instant.now()
        assertFailsWith<DataIntegrityViolationException> { trips.saveAndFlush(trip) }
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

    @Test
    fun `every road into a deleted trip's rows answers 404 — the rows exist, the trip does not`() {
        // Soft delete keeps every row so restore can work, which means each of these endpoints
        // resolves its row just fine and must then refuse over the trip. Any 200 here is a leak.
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()
        val me = creator.yourMemberId(tripId)
        val bob = creator.addMember(tripId, "Bob")
        val food = creator.builtInCategory(tripId)
        val itemId = creator.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me, bob))).id()
        check(
            creator.postFile("/api/items/$itemId/receipt", "bill.jpg", "image/jpeg", jpeg).statusCode == HttpStatus.OK,
        )
        val paybackId = creator
            .post(
                "/api/items/$itemId/paybacks",
                mapOf("fromMemberId" to bob.toString(), "amountMinor" to 500, "paidOn" to "2026-08-18"),
            ).id()

        creator.delete("/api/trips/$tripId")

        val expectations = mapOf(
            "item detail" to creator.get("/api/items/$itemId"),
            "item edit" to creator.patch("/api/items/$itemId", mapOf("title" to "x")),
            "item delete" to creator.delete("/api/items/$itemId"),
            "new payback" to creator.post(
                "/api/items/$itemId/paybacks",
                mapOf("fromMemberId" to bob.toString(), "amountMinor" to 100, "paidOn" to "2026-08-18"),
            ),
            "payback undo" to creator.post("/api/paybacks/$paybackId/undo", nothing),
            "settlement" to creator.get("/api/trips/$tripId/settlement"),
            "new settlement" to creator.post(
                "/api/trips/$tripId/settlements",
                mapOf("toMemberId" to bob.toString(), "amountMinor" to 100),
            ),
            "remind" to creator.post("/api/trips/$tripId/remind", mapOf("memberId" to bob.toString())),
            "categories" to creator.get("/api/trips/$tripId/categories"),
            "csv export" to creator.get("/api/trips/$tripId/expenses.csv"),
            "close" to creator.post("/api/trips/$tripId/close", nothing),
            "reopen" to creator.post("/api/trips/$tripId/reopen", nothing),
            "hide" to creator.post("/api/trips/$tripId/hide", nothing),
            "unhide" to creator.post("/api/trips/$tripId/unhide", nothing),
            "add member" to creator.post("/api/trips/$tripId/members", mapOf("displayName" to "Zed")),
            "invite" to creator.post("/api/trips/$tripId/invite", nothing),
        )
        for ((road, response) in expectations) {
            assertEquals(HttpStatus.NOT_FOUND, response.statusCode, "$road on a deleted trip: ${response.body}")
        }
        assertEquals(
            HttpStatus.NOT_FOUND,
            creator.getBytes("/api/items/$itemId/receipt").statusCode,
            "receipt image on a deleted trip",
        )
    }

    @Test
    fun `the sweep takes a deleted trip's receipt images with it — objects first, rows by cascade`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()
        val me = creator.yourMemberId(tripId)
        val food = creator.builtInCategory(tripId)
        val itemId = creator.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me))).id()
        check(
            creator.postFile("/api/items/$itemId/receipt", "bill.jpg", "image/jpeg", jpeg).statusCode == HttpStatus.OK,
        )
        val objectName = receiptRows.findAllByTripId(tripId).single().objectName

        creator.delete("/api/trips/$tripId")
        val doomed = trips.findById(tripId).orElseThrow()
        doomed.deletedAt = Instant.now().minus(Duration.ofDays(31))
        trips.save(doomed)

        assertEquals(1, purge.purgeDeleted(), "one trip past its window")
        assertNull(receiptObjects.fetch(objectName), "the image bytes are gone from storage, not orphaned in it")
        assertTrue(receiptRows.findAllByTripId(tripId).isEmpty(), "and the rows went with the cascade")
    }

    @Test
    fun `hiding an ended trip twice is idempotent, and the second call does not move the stamp`() {
        // Unlike close, hide carries no clock hanging off its timestamp, so a retry is a no-op
        // rather than a 409 — and the stamp must not move on the second call, the same way the
        // delete-retry above keeps purgesAt fixed.
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()
        creator.end(tripId)

        assertEquals(HttpStatus.OK, creator.post("/api/trips/$tripId/hide", nothing).statusCode)
        // Read the persisted stamp, not the mutation response: hide sets clock.instant() at full
        // nanosecond precision in memory, while Postgres keeps micros — so a response compared
        // against a later DB read would differ on the truncated digits alone. DB read vs DB read.
        val stamp = creator.get("/api/trips/$tripId").json()["hiddenAt"].asText()

        val second = creator.post("/api/trips/$tripId/hide", nothing)
        assertEquals(HttpStatus.OK, second.statusCode, "hiding an already-hidden trip: ${second.body}")
        assertEquals(
            stamp,
            creator.get("/api/trips/$tripId").json()["hiddenAt"].asText(),
            "a second hide must not restamp the trip",
        )
    }

    @Test
    fun `unhiding a trip that was never hidden just reports it is not hidden`() {
        // Idempotent for the same reason as hide: a retry of an unhide that already landed — or one
        // that was never needed — answers with the trip, not an argument.
        val creator = signedIn("Nora")
        val tripId = creator.createTrip()
        creator.end(tripId)

        val unhidden = creator.post("/api/trips/$tripId/unhide", nothing)

        assertEquals(HttpStatus.OK, unhidden.statusCode, "unhiding a never-hidden trip: ${unhidden.body}")
        assertTrue(unhidden.json()["hiddenAt"].isNull, "and it reports not hidden")
    }
}
