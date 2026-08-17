package app.ledger.server.receipt

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/**
 * The pointer to one expense's receipt image. The bytes live behind [ReceiptStorage]; this row
 * is what makes them findable, servable and — fourteen days after the trip ends — deletable.
 */
@Entity
@Table(name = "item_receipts")
class ReceiptEntity(
    /** One receipt per expense, so the expense id is the identity. */
    @Id
    @Column(name = "item_id")
    val itemId: UUID,
    @Column(name = "trip_id", nullable = false, updatable = false)
    val tripId: UUID,
    /** `tripId/itemId/version` in the object store. Replaced wholesale on every re-upload. */
    @Column(name = "object_name", nullable = false)
    var objectName: String,
    @Column(name = "content_type", nullable = false)
    var contentType: String,
    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long,
    @Column(name = "uploaded_by_user_id", nullable = false)
    var uploadedByUserId: UUID,
    @Column(name = "uploaded_at", nullable = false)
    var uploadedAt: Instant,
    /**
     * Optimistic lock — called `revision` because `version` is taken by the URL-facing concept
     * below. Two replaces from the same starting state cannot both land: the loser is refused
     * (409) and deletes the object it had already stored, instead of leaving bytes in the bucket
     * that no row references and the sweep can never find.
     */
    @Version
    @Column(name = "revision")
    var revision: Long = 0,
) {
    /**
     * The cache-busting half of the image URL — the last segment of [objectName]. A replace mints
     * a new one, which is what lets the GET endpoint answer with `immutable`.
     */
    val version: String
        get() = objectName.substringAfterLast('/')
}
