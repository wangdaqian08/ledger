package app.ledger.server.payback

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** PENDING moves no number anywhere. Only APPROVED counts towards anybody's balance. */
enum class PaybackStatusName { PENDING, APPROVED, REJECTED }

/**
 * Money handed back from one member to another.
 *
 * With an [itemId] it is a repayment towards that bill, and [toMemberId] is that bill's payer.
 * With no item it is a trip-level settlement from the Settle-up screen, which is why the recipient
 * is stored rather than inferred. One record type, one state machine, one approval rule (§7a) —
 * and so no way for an item repayment and a settlement between the same two people to both
 * subtract.
 */
@Entity
@Table(name = "paybacks")
class PaybackEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "trip_id", nullable = false, updatable = false)
    val tripId: UUID,
    @Column(name = "item_id", updatable = false)
    val itemId: UUID? = null,
    @Column(name = "from_member_id", nullable = false, updatable = false)
    val fromMemberId: UUID,
    @Column(name = "to_member_id", nullable = false, updatable = false)
    val toMemberId: UUID,
    @Column(name = "amount_minor", nullable = false)
    var amountMinor: Long,
    @Column(name = "paid_on", nullable = false)
    var paidOn: LocalDate,
    /** Cloud Storage object name. Uploading it is build order step 7. */
    @Column(name = "proof_object_name")
    var proofObjectName: String? = null,
    @Column
    var note: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaybackStatusName,
    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    val createdByUserId: UUID,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "reviewed_by_user_id")
    var reviewedByUserId: UUID? = null,
    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,
    @Column(name = "reject_reason")
    var rejectReason: String? = null,
)

interface PaybackRepository : JpaRepository<PaybackEntity, UUID> {
    fun findAllByTripIdOrderByCreatedAt(tripId: UUID): List<PaybackEntity>

    fun findAllByTripIdInOrderByCreatedAt(tripIds: Collection<UUID>): List<PaybackEntity>

    fun findAllByItemIdOrderByCreatedAt(itemId: UUID): List<PaybackEntity>
}
