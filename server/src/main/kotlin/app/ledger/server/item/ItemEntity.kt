package app.ledger.server.item

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Stored as text with a CHECK constraint rather than a Postgres enum, which is painful to alter. */
enum class SplitRuleName { EQUAL, WEIGHTED, EXACT }

/**
 * Money that left the group, paid by exactly one person.
 *
 * Note what is *not* here: nobody's share. Shares are derived on every read by the engine, which
 * is what lets a corrected people list flow straight through to every balance with nothing stale
 * left behind. [ItemShareEntity] holds the inputs to that derivation, never its results.
 */
@Entity
@Table(name = "items")
class ItemEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "trip_id", nullable = false, updatable = false)
    val tripId: UUID,
    @Column(nullable = false)
    var title: String,
    @Column(name = "category_id", nullable = false)
    var categoryId: UUID,
    @Column(name = "amount_minor", nullable = false)
    var amountMinor: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "split_rule", nullable = false)
    var splitRule: SplitRuleName,
    @Column(name = "payer_member_id", nullable = false)
    var payerMemberId: UUID,
    @Column(name = "spent_on", nullable = false)
    var spentOn: LocalDate,
    @Column
    var note: String? = null,
    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    val createdByUserId: UUID,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    /** The column has a default, but nothing in Postgres advances it — the service must. */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

@Embeddable
data class ItemShareId(
    @Column(name = "item_id")
    val itemId: UUID,
    @Column(name = "member_id")
    val memberId: UUID,
) : Serializable

/**
 * One row per person on an item's people list — the primitive the whole design rests on.
 *
 * [weight] is set when the parent item splits WEIGHTED and [exactAmountMinor] when it splits
 * EXACT; both are null for EQUAL. Which one applies depends on the parent's split rule, so it is
 * a cross-table rule and lives in the service rather than in a CHECK.
 */
@Entity
@Table(name = "item_shares")
class ItemShareEntity(
    @EmbeddedId
    val id: ItemShareId,
    /** Carried so the foreign keys can be composite and cannot reach into another trip. */
    @Column(name = "trip_id", nullable = false)
    val tripId: UUID,
    @Column
    var weight: Int? = null,
    @Column(name = "exact_amount_minor")
    var exactAmountMinor: Long? = null,
)
