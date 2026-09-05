package app.ledger.server.settlement

import app.ledger.server.MAX_AMOUNT_MINOR
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import java.util.*

/** The Pay button. A request to the person owed, not an act — it settles nothing on its own. */
data class SubmitSettlement(
    val toMemberId: UUID,
    @field:Positive(message = "a settlement has to be for some money")
    @field:Max(MAX_AMOUNT_MINOR)
    val amountMinor: Long,
)

data class Remind(val memberId: UUID)

data class FamilyInput(
    @field:NotEmpty(message = "a family can't be empty")
    val memberIds: List<UUID>,
)

/** Whatever explicit Families the view has built so far (7b). Not persisted. */
data class PreviewFamilies(
    @field:Valid val families: List<FamilyInput> = emptyList(),
)
