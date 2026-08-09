package app.ledger.server.settlement

import jakarta.validation.constraints.Positive
import java.util.UUID

/** The Pay button. A request to the person owed, not an act — it settles nothing on its own. */
data class SubmitSettlement(
    val toMemberId: UUID,
    @field:Positive(message = "a settlement has to be for some money")
    val amountMinor: Long,
)

data class Remind(val memberId: UUID)
