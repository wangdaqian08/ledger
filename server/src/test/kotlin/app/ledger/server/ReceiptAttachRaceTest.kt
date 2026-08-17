package app.ledger.server

import app.ledger.server.receipt.ReceiptEntity
import app.ledger.server.receipt.ReceiptProperties
import app.ledger.server.receipt.ReceiptRepository
import app.ledger.server.receipt.ReceiptStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.util.Optional
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Two people photograph the same bill at the same moment — the payer and the creator both may.
 * One of them must lose, and the loser has already stored bytes by the time it finds out. Losing
 * has to mean a 409 and a cleaned-up object: a 500 confuses, and an unreferenced object sits in
 * the bucket forever, invisible to the retention sweep, which only ever walks rows.
 *
 * Deterministic without threads, in the two house styles: the first-attach race blinds the
 * existing-row pre-check once (ItemCreateRaceTest's trick) so the loser drives into the real
 * primary key; the replace race is forced the way OptimisticLockingTest forces it.
 */
class ReceiptAttachRaceTest : ApiTest() {
    @MockitoSpyBean
    private lateinit var receipts: ReceiptRepository

    @Autowired
    private lateinit var objects: ReceiptStorage

    @Autowired
    private lateinit var properties: ReceiptProperties

    @Autowired
    private lateinit var txManager: PlatformTransactionManager

    private val jpegA = byteArrayOf(-1, -40, -1, -32) + ByteArray(80) { it.toByte() }
    private val jpegB = byteArrayOf(-1, -40, -1, -32) + ByteArray(90) { (it * 2).toByte() }

    private class Documented(val client: SessionAwareClient, val tripId: UUID, val itemId: UUID)

    private fun expenseOnATrip(): Documented {
        val client = signedIn("Nora")
        val tripId = client.createTrip()
        val me = client.yourMemberId(tripId)
        val food = client.builtInCategory(tripId)
        val itemId = client.post("/api/trips/$tripId/items", expense("Dinner", 3000, food, me, listOf(me))).id()
        return Documented(client, tripId, itemId)
    }

    /** How many objects the local store holds for one expense — the leak detector. */
    private fun storedObjectCount(d: Documented): Long {
        val dir = properties.localDir.resolve(d.tripId.toString()).resolve(d.itemId.toString())
        return Files.list(dir).use { it.count() }
    }

    @Test
    fun `an upload that loses the first-attach race gets a conflict, and its object is cleaned up`() {
        val d = expenseOnATrip()
        val path = "/api/items/${d.itemId}/receipt"
        assertEquals(HttpStatus.OK, d.client.postFile(path, "winner.jpg", "image/jpeg", jpegA).statusCode)

        // Blind the existing-row check: the next attach takes the first-time insert path — what a
        // true concurrent loser does — and meets item_receipts' real primary key. Reset afterwards
        // (a delegate spy cannot fall back per-call) so the verifying reads see the truth again.
        Mockito.doReturn(Optional.empty<ReceiptEntity>()).`when`(receipts).findById(d.itemId)
        val loser = d.client.postFile(path, "loser.jpg", "image/jpeg", jpegB)
        Mockito.reset(receipts)

        assertEquals(HttpStatus.CONFLICT, loser.statusCode, "the race loser must be told, not 500'd: ${loser.body}")
        assertTrue(
            loser.json()["detail"].asText().contains("receipt", ignoreCase = true),
            "the reason should say what collided: ${loser.body}",
        )
        assertContentEquals(jpegA, d.client.getBytes(path).body, "the winner's photo must stand")
        assertEquals(1, storedObjectCount(d), "the losing upload must not leave an unreferenced object behind")
    }

    @Test
    fun `a replace that loses the version race gets a conflict, and its object is cleaned up`() {
        val d = expenseOnATrip()
        val path = "/api/items/${d.itemId}/receipt"
        assertEquals(HttpStatus.OK, d.client.postFile(path, "current.jpg", "image/jpeg", jpegA).statusCode)

        // The moment a concurrent replace wins: this flush finds the revision has moved on.
        Mockito
            .doThrow(ObjectOptimisticLockingFailureException(ReceiptEntity::class.java, d.itemId))
            .`when`(receipts)
            .flush()
        val loser = d.client.postFile(path, "stale-replace.jpg", "image/jpeg", jpegB)
        Mockito.reset(receipts)

        assertEquals(HttpStatus.CONFLICT, loser.statusCode, "a lost replace is a conflict: ${loser.body}")
        assertContentEquals(jpegA, d.client.getBytes(path).body, "the standing photo must survive the lost replace")
        assertEquals(1, storedObjectCount(d), "the losing replace must delete the object it had stored")
    }

    @Test
    fun `a stale receipt row cannot silently overwrite a newer one`() {
        val d = expenseOnATrip()
        d.client.postFile("/api/items/${d.itemId}/receipt", "first.jpg", "image/jpeg", jpegA)
        val tx = TransactionTemplate(txManager)

        // Two replaces open the same row; one commits first.
        val stale = receipts.findById(d.itemId).orElseThrow()
        tx.executeWithoutResult {
            val current = receipts.findById(d.itemId).orElseThrow()
            current.objectName = "${d.tripId}/${d.itemId}/${UUID.randomUUID()}"
        }

        // The other now writes the copy it opened before that landed. It must be refused.
        assertThrows<ObjectOptimisticLockingFailureException> {
            tx.executeWithoutResult {
                stale.objectName = "${d.tripId}/${d.itemId}/${UUID.randomUUID()}"
                receipts.saveAndFlush(stale)
            }
        }
    }
}
