package app.ledger.server

import app.ledger.server.receipt.ReceiptRepository
import app.ledger.server.receipt.ReceiptRetention
import app.ledger.server.receipt.ReceiptStorage
import app.ledger.server.trip.TripRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The 14-day retention rule (spec §3): receipt images exist to be checked while a finished trip
 * settles up, and after that window they are deleted — object, row and pointer — while the
 * expense itself keeps every number. The sweep runs daily in production; here it is invoked
 * directly, with `closed_at` backdated through the repository, because a test that waits two
 * weeks is not a test.
 */
class ReceiptRetentionApiTest : ApiTest() {
    @Autowired
    private lateinit var retention: ReceiptRetention

    @Autowired
    private lateinit var trips: TripRepository

    @Autowired
    private lateinit var rows: ReceiptRepository

    @Autowired
    private lateinit var objects: ReceiptStorage

    private val jpeg = byteArrayOf(-1, -40, -1, -32) + ByteArray(120) { it.toByte() }

    private class Documented(val client: SessionAwareClient, val tripId: UUID, val itemId: UUID, val objectName: String)

    private fun documentedTrip(): Documented {
        val client = signedIn("Nora")
        val tripId = client.createTrip()
        val me = client.yourMemberId(tripId)
        val food = client.builtInCategory(tripId)
        val itemId = client.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me))).id()
        client.postFile("/api/items/$itemId/receipt", "receipt.jpg", "image/jpeg", jpeg)
        return Documented(client, tripId, itemId, rows.findById(itemId).orElseThrow().objectName)
    }

    private fun backdateClose(tripId: UUID, days: Long) {
        val trip = trips.findById(tripId).orElseThrow()
        trip.closedAt = Instant.now().minus(Duration.ofDays(days))
        trips.save(trip)
    }

    @Test
    fun `fourteen days after a trip ends its receipts are gone, and the numbers stay`() {
        val ended = documentedTrip()
        ended.client.post("/api/trips/${ended.tripId}/close", emptyMap<String, String>())
        backdateClose(ended.tripId, days = 15)

        val removed = retention.purgeExpired()

        assertTrue(removed >= 1, "the sweep should have removed the ended trip's receipt")
        assertNull(objects.fetch(ended.objectName), "the image bytes must be deleted")
        assertTrue(rows.findById(ended.itemId).isEmpty, "the pointer row goes with them")
        assertEquals(HttpStatus.NOT_FOUND, ended.client.getBytes("/api/items/${ended.itemId}/receipt").statusCode)

        val item = ended.client.get("/api/items/${ended.itemId}").json()
        assertTrue(item["receipt"].isNull, "the view stops pointing at a deleted image")
        assertEquals(3000, item["amountMinor"].asLong(), "only the image goes — never the money record")

        assertEquals(0, retention.purgeExpired(), "a second sweep finds nothing left to do")
    }

    @Test
    fun `open trips and freshly ended trips keep their receipts`() {
        val open = documentedTrip()
        val justEnded = documentedTrip()
        justEnded.client.post("/api/trips/${justEnded.tripId}/close", emptyMap<String, String>())
        val insideWindow = documentedTrip()
        insideWindow.client.post("/api/trips/${insideWindow.tripId}/close", emptyMap<String, String>())
        backdateClose(insideWindow.tripId, days = 13)

        retention.purgeExpired()

        assertNotNull(objects.fetch(open.objectName), "an open trip is not even on the clock")
        assertNotNull(objects.fetch(justEnded.objectName), "day zero is not day fourteen")
        assertNotNull(objects.fetch(insideWindow.objectName), "day thirteen is still inside the window")
    }

    @Test
    fun `reopening a trip stops the clock`() {
        val saved = documentedTrip()
        saved.client.post("/api/trips/${saved.tripId}/close", emptyMap<String, String>())
        backdateClose(saved.tripId, days = 15)
        saved.client.post("/api/trips/${saved.tripId}/reopen", emptyMap<String, String>())

        retention.purgeExpired()

        assertNotNull(objects.fetch(saved.objectName), "a reopened trip is an open trip")
        assertEquals(HttpStatus.OK, saved.client.getBytes("/api/items/${saved.itemId}/receipt").statusCode)
    }
}
