package app.ledger.server.receipt

import app.ledger.server.item.ItemRepository
import app.ledger.server.item.ItemView
import app.ledger.server.item.toView
import app.ledger.server.trip.TripAccess
import app.ledger.server.trip.TripSnapshots
import jakarta.persistence.EntityManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.util.UUID

@Service
class ReceiptService(
    private val receipts: ReceiptRepository,
    private val items: ItemRepository,
    private val snapshots: TripSnapshots,
    private val access: TripAccess,
    private val storage: ReceiptStorage,
    private val clock: Clock,
    private val entityManager: EntityManager,
) {
    /**
     * Item payer plus trip creator, like every change to an expense (spec §5), and only while the
     * trip is open. Declared content types are trusted as far as serving goes — the GET answers
     * with this type plus `nosniff`, so a mislabelled file renders broken rather than executing.
     */
    @Transactional
    fun attach(itemId: UUID, contentType: String?, bytes: ByteArray, actor: UUID): ItemView {
        val item = items.findById(itemId).orElseThrow { noSuchItem() }
        val trip = access.expenseEditorOnly(item.tripId, item.payerMemberId, actor)
        access.requireOpen(trip)

        val declared = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (declared == null || declared !in IMAGE_TYPES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A receipt has to be a JPEG, PNG or WebP image")
        }
        if (bytes.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "There is no image in this upload")
        }

        // The object first, the row second: a row pointing at bytes that never landed would be a
        // broken image on somebody's screen, while a crash in between leaves only an unreferenced
        // object no user can ever see.
        val objectName = "${item.tripId}/${item.id}/${UUID.randomUUID()}"
        storage.put(objectName, declared, bytes)

        val existing = receipts.findById(item.id).orElse(null)
        if (existing == null) {
            // persist, not save: the id is the item's own, so save would merge — a SELECT-then-
            // INSERT that quietly turns a race loser's insert into an UPDATE of the winner's row,
            // silently swapping the photo and orphaning the winner's object (same trap, same cure
            // as ItemService.create). persist always INSERTs, so the loser meets the primary key
            // head-on and is answered below.
            entityManager.persist(
                ReceiptEntity(
                    itemId = item.id,
                    tripId = item.tripId,
                    objectName = objectName,
                    contentType = declared,
                    sizeBytes = bytes.size.toLong(),
                    uploadedByUserId = actor,
                    uploadedAt = clock.instant(),
                ),
            )
        } else {
            deleteObjectAfterCommit(existing.objectName)
            existing.objectName = objectName
            existing.contentType = declared
            existing.sizeBytes = bytes.size.toLong()
            existing.uploadedByUserId = actor
            existing.uploadedAt = clock.instant()
        }
        try {
            receipts.flush()
        } catch (raceLost: DataIntegrityViolationException) {
            // Two first-time uploads passed the existing-row check together — payer and creator
            // photographing the same bill — and the other's insert landed. The object stored above
            // is referenced by nothing, and afterCommit will never come for this transaction, so
            // it goes now; the sweep only walks rows and could never find it.
            storage.delete(objectName)
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "A receipt landed on this expense just now — try again to replace it",
                raceLost,
            )
        } catch (raceLost: ObjectOptimisticLockingFailureException) {
            // Two replaces raced and the other's revision landed first. Same cleanup; the global
            // 409 mapping tells the loser to reopen and try again.
            storage.delete(objectName)
            throw raceLost
        }

        val snapshot = snapshots.load(item.tripId)
        return snapshot.toView(snapshot.items.first { it.id == item.id }, actor)
    }

    /**
     * Any trip member — and deliberately not behind [TripAccess.requireOpen]: an ended trip keeps
     * its receipts readable for the retention window, because that is when people settle up.
     */
    @Transactional(readOnly = true)
    fun open(itemId: UUID, actor: UUID): StoredReceipt {
        val item = items.findById(itemId).orElseThrow { noSuchItem() }
        access.visibleTrip(item.tripId, actor)
        val receipt = receipts.findById(item.id).orElseThrow { noReceipt() }
        val bytes = storage.fetch(receipt.objectName) ?: throw noReceipt()
        return StoredReceipt(receipt.contentType, bytes)
    }

    @Transactional
    fun remove(itemId: UUID, actor: UUID) {
        val item = items.findById(itemId).orElseThrow { noSuchItem() }
        val trip = access.expenseEditorOnly(item.tripId, item.payerMemberId, actor)
        access.requireOpen(trip)
        val receipt = receipts.findById(item.id).orElseThrow { noReceipt() }

        receipts.delete(receipt)
        deleteObjectAfterCommit(receipt.objectName)
    }

    /**
     * Housekeeping for [app.ledger.server.item.ItemService.delete]: the caller has already decided
     * who may delete the expense and the cascade takes the row — this removes the object the row
     * pointed at, which no cascade can reach.
     */
    @Transactional
    fun removeForDeletedItem(itemId: UUID) {
        receipts.findById(itemId).ifPresent { deleteObjectAfterCommit(it.objectName) }
    }

    /**
     * Storage deletes ride the commit. Deleting eagerly would let a rollback leave a row pointing
     * at nothing; deleting after commit means a rollback keeps both halves. A crash in the gap
     * leaves an unreferenced object — invisible to users, and rare enough to clean by hand.
     */
    private fun deleteObjectAfterCommit(objectName: String) {
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() = storage.delete(objectName)
            },
        )
    }

    private companion object {
        val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")

        fun noSuchItem() = ResponseStatusException(HttpStatus.NOT_FOUND, "No such expense")

        fun noReceipt() = ResponseStatusException(HttpStatus.NOT_FOUND, "This expense has no receipt")
    }
}
