package app.ledger.server

import app.ledger.server.receipt.ReceiptRepository
import app.ledger.server.receipt.ReceiptStorage
import app.ledger.server.trip.TripPurge
import app.ledger.server.trip.TripRepository
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

/**
 * The purge commits one trip at a time, not one transaction around the whole sweep (see
 * [TripPurge]). So a trip whose receipt object refuses to delete costs exactly that trip — it stays
 * on tomorrow's list — and cannot roll back the night's other purges, whose storage objects are
 * already gone by then.
 *
 * Proven by poisoning exactly one trip's storage delete and watching the healthy trip still go. Its
 * own class, with a [ReceiptStorage] spy, so [TripHideAndDeleteApiTest]'s @Autowired storage stays a
 * real one — the same reason [ReceiptAttachRaceTest] is separate from the tests it neighbours.
 */
class TripPurgeIsolationTest : ApiTest() {
    @MockitoSpyBean
    private lateinit var storage: ReceiptStorage

    @Autowired
    private lateinit var purge: TripPurge

    @Autowired
    private lateinit var trips: TripRepository

    @Autowired
    private lateinit var receiptRows: ReceiptRepository

    private val jpeg = byteArrayOf(-1, -40, -1, -32) + ByteArray(120) { it.toByte() }

    /** A trip with one documented expense, deleted and backdated past the restore window. */
    private fun documentedAndDeleted(client: SessionAwareClient): Pair<UUID, String> {
        val tripId = client.createTrip()
        val me = client.yourMemberId(tripId)
        val food = client.builtInCategory(tripId)
        val itemId = client.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me))).id()
        check(
            client.postFile("/api/items/$itemId/receipt", "bill.jpg", "image/jpeg", jpeg).statusCode == HttpStatus.OK,
        )
        val objectName = receiptRows.findAllByTripId(tripId).single().objectName
        client.delete("/api/trips/$tripId")
        // Backdating beats waiting thirty days; the sweep reads the clock, not the calendar.
        val trip = trips.findById(tripId).orElseThrow()
        trip.deletedAt = Instant.now().minus(Duration.ofDays(31))
        trips.save(trip)
        return tripId to objectName
    }

    @Test
    fun `one trip whose receipt will not delete does not block the purge of another`() {
        val nora = signedIn("Nora")
        val (poisoned, poisonedObject) = documentedAndDeleted(nora)
        val (healthy, _) = documentedAndDeleted(nora)

        // Only the poisoned trip's object refuses to go; every other delete runs for real.
        Mockito.doThrow(RuntimeException("storage is having a moment")).`when`(storage).delete(poisonedObject)

        assertEquals(1, purge.purgeDeleted(), "the healthy trip is purged; the poisoned one is not counted")
        assertTrue(trips.findById(poisoned).isPresent, "the poisoned trip survives to be retried tomorrow")
        assertTrue(trips.findById(healthy).isEmpty, "the healthy trip is gone, cascade and all")

        // Clean up, so the leftover backdated-deleted trip does not inflate another class's sweep:
        // with storage healthy again, the retry the log promised lands.
        Mockito.reset(storage)
        purge.purgeDeleted()
        assertTrue(trips.findById(poisoned).isEmpty, "and once storage recovers, the poisoned trip purges too")
    }
}
