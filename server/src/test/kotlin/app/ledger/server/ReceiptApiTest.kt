package app.ledger.server

import app.ledger.server.receipt.ReceiptRepository
import app.ledger.server.receipt.ReceiptStorage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Receipt images on an expense (spec §5 · §6). The bytes live behind [ReceiptStorage]; one row
 * points at them; only the trip's members ever see them. Changing a receipt follows the expense's
 * own edit right — payer plus creator — and an ended trip keeps receipts readable while refusing
 * changes, because the 14-day window exists precisely so people can check them while settling.
 *
 * The storage peeks below are the one white-box element: a leaked or lingering object is invisible
 * over HTTP by design, and invisible is exactly how storage bills grow.
 */
class ReceiptApiTest : ApiTest() {
    @Autowired
    private lateinit var rows: ReceiptRepository

    @Autowired
    private lateinit var objects: ReceiptStorage

    private val jpeg = byteArrayOf(-1, -40, -1, -32) + ByteArray(300) { it.toByte() }
    private val png = byteArrayOf(-119, 80, 78, 71) + ByteArray(200) { (it * 3).toByte() }

    private class Party(
        val creator: SessionAwareClient,
        val friend: SessionAwareClient,
        val tripId: UUID,
        val me: UUID,
        val seat: UUID,
        val itemId: UUID,
        val food: UUID,
    )

    private fun party(): Party {
        val creator = signedIn("Nora")
        val friend = signedIn("Piet")
        val tripId = creator.createTrip()
        val me = creator.yourMemberId(tripId)
        val seat = creator.addMember(tripId, "Piet")
        friend.claim(tripId, creator.invite(tripId), seat)
        val food = creator.builtInCategory(tripId)
        val itemId = creator.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me, seat))).id()
        return Party(creator, friend, tripId, me, seat, itemId, food)
    }

    @Test
    fun `the payer attaches a receipt and any member reads back the exact bytes`() {
        val p = party()

        val attached = p.creator.postFile("/api/items/${p.itemId}/receipt", "receipt.jpg", "image/jpeg", jpeg)
        assertEquals(HttpStatus.OK, attached.statusCode, "upload failed: ${attached.body}")
        val version = attached.json()["receipt"]["version"].asText()
        assertTrue(version.isNotBlank(), "the item view should now point at its receipt")

        val image = p.friend.getBytes("/api/items/${p.itemId}/receipt")
        assertEquals(HttpStatus.OK, image.statusCode)
        assertEquals("image/jpeg", image.headers.contentType.toString())
        assertContentEquals(jpeg, image.body, "the bytes that went in must come back")
        val cache = image.headers[HttpHeaders.CACHE_CONTROL]?.firstOrNull().orEmpty()
        assertTrue(
            "immutable" in cache && "private" in cache,
            "versioned URLs never change content, and the image is nobody else's business: got '$cache'",
        )
        assertEquals(
            "nosniff",
            image.headers["X-Content-Type-Options"]?.firstOrNull(),
            "a declared type is trusted for serving only because nosniff stops a lie from executing",
        )

        assertEquals(
            version,
            p.friend
                .get("/api/items/${p.itemId}")
                .json()["receipt"]["version"]
                .asText(),
            "the detail sheet needs the pointer",
        )
        val inTrip = p.friend
            .get("/api/trips/${p.tripId}")
            .json()["items"]
            .first { it["id"].asText() == p.itemId.toString() }
        assertEquals(version, inTrip["receipt"]["version"].asText(), "the trip payload carries it too")
    }

    @Test
    fun `an expense without a receipt says so — null in the view, 404 for the bytes`() {
        val p = party()

        assertTrue(
            p.creator
                .get("/api/items/${p.itemId}")
                .json()["receipt"]
                .isNull,
        )
        assertEquals(HttpStatus.NOT_FOUND, p.creator.getBytes("/api/items/${p.itemId}/receipt").statusCode)
        assertEquals(HttpStatus.NOT_FOUND, p.creator.delete("/api/items/${p.itemId}/receipt").statusCode)
    }

    @Test
    fun `replacing a receipt rotates its version and the replaced object is deleted`() {
        val p = party()
        p.creator.postFile("/api/items/${p.itemId}/receipt", "receipt.jpg", "image/jpeg", jpeg)
        val first = rows.findById(p.itemId).orElseThrow()
        val firstObject = first.objectName
        val firstVersion = first.version

        val replaced = p.creator.postFile("/api/items/${p.itemId}/receipt", "better.png", "image/png", png)
        assertEquals(HttpStatus.OK, replaced.statusCode, "replace failed: ${replaced.body}")
        assertNotEquals(
            firstVersion,
            replaced.json()["receipt"]["version"].asText(),
            "a replaced image must get a fresh URL, or immutable caches keep showing the old one",
        )

        val image = p.friend.getBytes("/api/items/${p.itemId}/receipt")
        assertEquals("image/png", image.headers.contentType.toString())
        assertContentEquals(png, image.body)
        assertNull(objects.fetch(firstObject), "the replaced object must not linger in the bucket")
    }

    @Test
    fun `attach and remove belong to the payer and the creator — a member gets 403, a stranger 404`() {
        val p = party()
        val stranger = signedIn("Sam")
        val path = "/api/items/${p.itemId}/receipt"

        val refused = p.friend.postFile(path, "receipt.jpg", "image/jpeg", jpeg)
        assertEquals(HttpStatus.FORBIDDEN, refused.statusCode)
        assertTrue(
            refused.json()["detail"].asText().contains("paid"),
            "the reason should name the rule: ${refused.body}",
        )
        assertEquals(HttpStatus.FORBIDDEN, p.friend.delete(path).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, stranger.postFile(path, "receipt.jpg", "image/jpeg", jpeg).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, stranger.getBytes(path).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, stranger.delete(path).statusCode)

        // The friend pays the next bill, so it is theirs to document — and still the creator's.
        val friendsItem = p.friend
            .post("/api/trips/${p.tripId}/items", expense("Taxi", 900, p.food, p.seat, listOf(p.me, p.seat)))
            .id()
        assertEquals(
            HttpStatus.OK,
            p.creator.postFile("/api/items/$friendsItem/receipt", "creator.jpg", "image/jpeg", jpeg).statusCode,
            "the creator can correct any expense, receipts included",
        )
        assertEquals(
            HttpStatus.OK,
            p.friend.postFile("/api/items/$friendsItem/receipt", "payer.png", "image/png", png).statusCode,
            "the payer documents their own bill",
        )
    }

    @Test
    fun `a receipt must be an image, and not an empty one`() {
        val p = party()
        val path = "/api/items/${p.itemId}/receipt"

        val wrongType = p.creator.postFile(path, "notes.txt", "text/plain", "not an image".toByteArray())
        assertEquals(HttpStatus.BAD_REQUEST, wrongType.statusCode)
        assertTrue(
            wrongType.json()["detail"].asText().contains("image", ignoreCase = true),
            "the refusal should say what is expected: ${wrongType.body}",
        )
        assertEquals(
            HttpStatus.BAD_REQUEST,
            p.creator.postFile(path, "receipt.jpg", "image/jpeg", ByteArray(0)).statusCode,
        )
    }

    @Test
    fun `webp is accepted, and a declared type with parameters or odd casing still matches`() {
        val p = party()
        val path = "/api/items/${p.itemId}/receipt"
        val webp = byteArrayOf(82, 73, 70, 70) + ByteArray(50) { it.toByte() }

        assertEquals(HttpStatus.OK, p.creator.postFile(path, "receipt.webp", "image/webp", webp).statusCode)
        assertEquals(
            HttpStatus.OK,
            p.creator.postFile(path, "receipt.jpg", "IMAGE/JPEG; charset=UTF-8", jpeg).statusCode,
            "a trailing parameter, or a differently-cased type, is still an image",
        )
    }

    @Test
    fun `an image over the cap is a 413 with a reason, not a dropped connection`() {
        val p = party()

        val over = p.creator.postFile(
            "/api/items/${p.itemId}/receipt",
            "huge.jpg",
            "image/jpeg",
            ByteArray(6 * 1024 * 1024),
        )
        assertEquals(413, over.statusCode.value(), "got ${over.statusCode}: ${over.body}")
        assertTrue(over.json()["detail"].asText().contains("5"), "the limit should be named: ${over.body}")
    }

    @Test
    fun `an ended trip keeps receipts readable but refuses changes to them`() {
        val p = party()
        val path = "/api/items/${p.itemId}/receipt"
        p.creator.postFile(path, "receipt.jpg", "image/jpeg", jpeg)

        p.creator.post("/api/trips/${p.tripId}/close", emptyMap<String, String>())

        assertEquals(
            HttpStatus.OK,
            p.friend.getBytes(path).statusCode,
            "the 14-day window exists so people can check receipts while settling",
        )
        assertEquals(HttpStatus.CONFLICT, p.creator.postFile(path, "late.jpg", "image/jpeg", jpeg).statusCode)
        assertEquals(HttpStatus.CONFLICT, p.creator.delete(path).statusCode)
    }

    @Test
    fun `the payer takes a receipt off and the object goes with it`() {
        val p = party()
        val path = "/api/items/${p.itemId}/receipt"
        p.creator.postFile(path, "receipt.jpg", "image/jpeg", jpeg)
        val objectName = rows.findById(p.itemId).orElseThrow().objectName

        assertEquals(HttpStatus.NO_CONTENT, p.creator.delete(path).statusCode)

        assertEquals(HttpStatus.NOT_FOUND, p.creator.getBytes(path).statusCode)
        assertTrue(
            p.creator
                .get("/api/items/${p.itemId}")
                .json()["receipt"]
                .isNull,
        )
        assertNull(objects.fetch(objectName), "removed means removed from storage too")
    }

    @Test
    fun `deleting the expense deletes its receipt object too`() {
        val p = party()
        p.creator.postFile("/api/items/${p.itemId}/receipt", "receipt.jpg", "image/jpeg", jpeg)
        val objectName = rows.findById(p.itemId).orElseThrow().objectName

        assertEquals(HttpStatus.NO_CONTENT, p.creator.delete("/api/items/${p.itemId}").statusCode)

        assertNull(objects.fetch(objectName), "an orphaned object would sit in the bucket forever")
        assertTrue(rows.findById(p.itemId).isEmpty, "the row goes with the expense")
    }
}
