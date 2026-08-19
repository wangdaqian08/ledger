package app.ledger.server

import app.ledger.server.trip.TripPurge
import app.ledger.server.trip.TripRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The 03:47 sweep and a restore, colliding. The sweep takes its work list outside any transaction,
 * so a trip can be listed as purgeable and then, before the per-trip delete runs, be restored by
 * its creator. Destroying it by id at that point would erase a trip the creator was just told is
 * back — the loss is silent and total.
 *
 * Deterministic without threads, the way [ReceiptAttachRaceTest] forces its races: the restore is
 * driven the instant the sweep re-reads this trip under its lock — the read that has to notice it
 * is no longer deleted. Stubbing that concrete-id read (rather than the list) keeps the trigger
 * off Mockito's `any()` matcher, which does not survive a Kotlin non-null parameter.
 */
class TripPurgeRaceTest : ApiTest() {
    @Autowired
    private lateinit var purge: TripPurge

    @MockitoSpyBean
    private lateinit var trips: TripRepository

    private val nothing = emptyMap<String, String>()

    @Test
    fun `a restore that lands after the work list is taken survives the sweep`() {
        val creator = signedIn("Nora")
        val tripId = creator.createTrip("Doomed")
        creator.delete("/api/trips/$tripId")

        // Genuinely purgeable: deleted past the window, so it is on tonight's list.
        val deleted = trips.findById(tripId).orElseThrow()
        deleted.deletedAt = Instant.now().minus(Duration.ofDays(31))
        trips.save(deleted)

        // The race: the sweep has its work list (this trip is on it) and is about to destroy it when
        // the creator restores. We land the restore in exactly that gap — driving it the moment the
        // per-trip block re-reads the row under its lock, just before the real read runs.
        Mockito
            .doAnswer { invocation ->
                creator.post("/api/trips/$tripId/restore", nothing)
                invocation.callRealMethod()
            }.`when`(trips)
            .findForPurge(tripId)

        val removed = purge.purgeDeleted()

        assertEquals(0, removed, "a restored trip is no longer the sweep's to destroy")
        assertTrue(trips.findById(tripId).isPresent, "the trip the creator just restored is still here")
        assertTrue(
            creator.get("/api/trips").json()["trips"].any { it["id"].asText() == tripId.toString() },
            "and it is back on their home list, not lost",
        )
    }
}
