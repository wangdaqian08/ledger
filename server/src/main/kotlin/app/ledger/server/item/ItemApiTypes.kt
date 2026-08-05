package app.ledger.server.item

import app.ledger.server.payback.PaybackView
import com.fasterxml.jackson.annotation.JsonUnwrapped
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

/**
 * One person on an item's people list, plus whichever input their split rule needs.
 *
 * [weight] applies to WEIGHTED, [exactAmountMinor] to EXACT, and neither to EQUAL. They are inputs
 * to the derivation, never the resulting share — no computed amount is ever stored.
 */
data class ShareInput(
    val memberId: UUID,
    val weight: Int? = null,
    val exactAmountMinor: Long? = null,
)

data class CreateItem(
    /**
     * Supplied by the client so the split's salt — and therefore who absorbs each spare cent — is
     * known before saving, which is what lets the SplitBar show amounts that will not move when it
     * is. Sending the same id twice returns the first item rather than making a second, so a retry
     * after a dropped connection cannot double an expense.
     *
     * Optional: omitted, the server mints one, and the split is still correct — only the preview
     * would have been a guess.
     */
    val id: UUID? = null,
    @field:NotBlank
    @field:Size(max = 120)
    val title: String,
    val categoryId: UUID,
    @field:PositiveOrZero
    val amountMinor: Long,
    val splitRule: SplitRuleName = SplitRuleName.EQUAL,
    val payerMemberId: UUID,
    val spentOn: LocalDate,
    @field:Size(max = 500)
    val note: String? = null,
    @field:NotEmpty(message = "an expense has to be shared by somebody")
    @field:Valid
    val sharedBy: List<ShareInput>,
)

/**
 * Absent means unchanged; present means replace. [sharedBy] in particular replaces the people list
 * wholesale — correcting it is the entire mechanism by which a changed headcount reaches everyone's
 * balance, and a partial update could not express "these fourteen, and nobody else".
 */
data class PatchItem(
    @field:Size(max = 120)
    val title: String? = null,
    val categoryId: UUID? = null,
    @field:PositiveOrZero
    val amountMinor: Long? = null,
    val splitRule: SplitRuleName? = null,
    val payerMemberId: UUID? = null,
    val spentOn: LocalDate? = null,
    @field:Size(max = 500)
    val note: String? = null,
    @field:Valid
    val sharedBy: List<ShareInput>? = null,
)

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
