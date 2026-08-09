package app.ledger.server.item

import app.ledger.server.payback.PaybackView
import com.fasterxml.jackson.annotation.JsonUnwrapped
import java.time.LocalDate
import java.util.UUID

data class SplitView(
    val memberId: UUID,
    /** What this person owes towards the item. Derived on read, every time. */
    val amountMinor: Long,
    val weight: Int? = null,
    val exactAmountMinor: Long? = null,
)

data class ItemView(
    val id: UUID,
    val tripId: UUID,
    val title: String,
    val categoryId: UUID,
    val amountMinor: Long,
    val splitRule: SplitRuleName,
    val payerMemberId: UUID,
    val spentOn: LocalDate,
    val note: String?,
    val splits: List<SplitView>,
    val yourShareMinor: Long,
    /**
     * OPEN or ALL_SQUARE, from the engine: square once every sharer bar the payer has covered
     * their portion with *approved* paybacks. A derived state, never a button (§7a).
     */
    val state: String,
)

/**
 * An item, and whether this request is what created it.
 *
 * [fresh] is false when the same client id has been seen before, which the controller turns into
 * 200 rather than 201 — the request was answered, but nothing new happened.
 */
data class CreatedItem(val item: ItemView, val fresh: Boolean)

/**
 * One expense with its paybacks, for the detail sheet's approval section.
 *
 * Kept separate from [ItemView] because paybacks are unbounded per item and only this screen wants
 * them — the trip payload deliberately carries items without them (spec §6).
 */
data class ItemDetailView(
    @get:JsonUnwrapped
    val item: ItemView,
    val paybacks: List<PaybackView>,
)
