package app.ledger.server.item

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
    /** OPEN or ALL_SQUARE, from the engine. Everything is OPEN until paybacks arrive at step 6. */
    val state: String,
)
