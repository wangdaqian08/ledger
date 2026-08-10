package app.ledger.server.item

import app.ledger.server.MAX_AMOUNT_MINOR
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
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
    @field:Max(MAX_AMOUNT_MINOR)
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
    @field:Max(MAX_AMOUNT_MINOR)
    val amountMinor: Long? = null,
    val splitRule: SplitRuleName? = null,
    val payerMemberId: UUID? = null,
    val spentOn: LocalDate? = null,
    @field:Size(max = 500)
    val note: String? = null,
    @field:Valid
    val sharedBy: List<ShareInput>? = null,
)
