package app.ledger.server.payback

import app.ledger.server.MAX_AMOUNT_MINOR
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

data class SubmitPayback(
    val fromMemberId: UUID,
    @field:Positive(message = "a payback has to be for some money")
    @field:Max(MAX_AMOUNT_MINOR)
    val amountMinor: Long,
    val paidOn: LocalDate,
    @field:Size(max = 500)
    val note: String? = null,
)

/** Correcting a claim that was turned down, which sends it back for review (spec §3). */
data class ResubmitPayback(
    @field:Positive
    @field:Max(MAX_AMOUNT_MINOR)
    val amountMinor: Long? = null,
    val paidOn: LocalDate? = null,
    @field:Size(max = 500)
    val note: String? = null,
)

data class RejectPayback(
    @field:NotBlank(message = "say why, so it can be put right")
    @field:Size(max = 300)
    val reason: String,
)
