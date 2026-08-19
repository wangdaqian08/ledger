package app.ledger.server

import app.ledger.server.receipt.ReceiptRepository
import app.ledger.server.receipt.ReceiptRetention
import app.ledger.server.receipt.ReceiptStorage
import app.ledger.server.trip.TripRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One receipt whose object refuses to delete must not stop the retention sweep clearing the rest
 * (see [ReceiptRetention]): the failing one is logged and left for tomorrow, and every other expired
 * receipt still goes.
 *
 * Its own class, with a [ReceiptStorage] spy, so [ReceiptRetentionApiTest]'s @Autowired storage
 * stays a real one rather than being converted to a spy under it.
 */
class ReceiptRetentionIsolationTest : ApiTest() {
    @MockitoSpyBean
    private lateinit var storage: ReceiptStorage

    @Autowired
    private lateinit var retention: ReceiptRetention

    @Autowired
    private lateinit var trips: TripRepository

    @Autowired
    private lateinit var rows: ReceiptRepository

    private val jpeg = byteArrayOf(-1, -40, -1, -32) + ByteArray(120) { it.toByte() }

    /** A documented expense on a trip ended long enough ago that its receipt is past the window. */
    private fun expiredReceipt(client: SessionAwareClient): Pair<UUID, String> {
        val tripId = client.createTrip()
        val me = client.yourMemberId(tripId)
        val food = client.builtInCategory(tripId)
        val itemId = client.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me))).id()
        client.postFile("/api/items/$itemId/receipt", "receipt.jpg", "image/jpeg", jpeg)
        val objectName = rows.findById(itemId).orElseThrow().objectName
        client.post("/api/trips/$tripId/close", emptyMap<String, String>())
        // Backdated past the 14-day retention window through the repository — a test that waits two
        // weeks is not a test.
        val trip = trips.findById(tripId).orElseThrow()
        trip.closedAt = Instant.now().minus(Duration.ofDays(15))
        trips.save(trip)
        return itemId to objectName
    }

    @Test
    fun `one receipt that will not delete does not stop the sweep clearing the rest`() {
        val nora = signedIn("Nora")
        val (poisonedItem, poisonedObject) = expiredReceipt(nora)
        val (healthyItem, _) = expiredReceipt(nora)

        Mockito.doThrow(RuntimeException("storage is having a moment")).`when`(storage).delete(poisonedObject)

        assertEquals(1, retention.purgeExpired(), "the healthy receipt goes; the poisoned one is not counted")
        assertTrue(rows.findById(poisonedItem).isPresent, "the poisoned receipt row survives for tomorrow's sweep")
        assertTrue(rows.findById(healthyItem).isEmpty, "the healthy receipt row is gone")

        // Clean up, so the leftover expired receipt does not inflate another class's sweep: with
        // storage healthy again, the retry the log promised lands.
        Mockito.reset(storage)
        retention.purgeExpired()
        assertTrue(rows.findById(poisonedItem).isEmpty, "and once storage recovers, the poisoned receipt goes too")
    }
}
