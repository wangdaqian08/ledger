package app.ledger.server.settlement

import app.ledger.server.auth.LedgerPrincipal
import app.ledger.server.payback.PaybackView
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * One row of the Settle-up screen: your position with one other person.
 *
 * [owedMinor] is positive when you owe them and negative when they owe you. Rows are bilateral by
 * design (§7a) — not the shortest set of transfers, which would pair up people who have no row on
 * this screen at all.
 */
data class SettlementRow(
    val memberId: UUID,
    val displayName: String,
    val personHue: Short,
    val owedMinor: Long,
    /**
     * Trip-level claims between the two of you that nobody has decided yet, in either direction.
     * A pending claim is why the row can read "sent for confirmation" while still counting as
     * unpaid — because that is exactly what it is.
     */
    val pending: List<PaybackView>,
)

data class SettlementView(
    val rows: List<SettlementRow>,
    /** Positive means the group owes you. Equal to minus the sum of [rows], by construction. */
    val yourNetMinor: Long,
    /** Derived, never a button (§7a): everyone is square when no row has anything left on it. */
    val allSquare: Boolean,
)

/** The Pay button. A request to the person owed, not an act — it settles nothing on its own. */
data class SubmitSettlement(
    val toMemberId: UUID,
    @field:Positive(message = "a settlement has to be for some money")
    val amountMinor: Long,
)

data class Remind(val memberId: UUID)

@RestController
@RequestMapping("/api/trips/{tripId}")
class SettlementController(private val settlements: SettlementService) {
    @GetMapping("/settlement")
    fun settlement(
        @PathVariable tripId: UUID,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): SettlementView = settlements.forViewer(tripId, principal.userId)

    @PostMapping("/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    fun pay(
        @PathVariable tripId: UUID,
        @Valid @RequestBody command: SubmitSettlement,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): PaybackView = settlements.pay(tripId, command, principal.userId)

    @PostMapping("/remind")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remind(
        @PathVariable tripId: UUID,
        @Valid @RequestBody command: Remind,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ) = settlements.remind(tripId, command, principal.userId)
}
