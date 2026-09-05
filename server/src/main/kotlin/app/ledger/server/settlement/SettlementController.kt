package app.ledger.server.settlement

import app.ledger.server.auth.LedgerPrincipal
import app.ledger.server.payback.PaybackView
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

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

    @PostMapping("/families")
    fun families(
        @PathVariable tripId: UUID,
        @Valid @RequestBody command: PreviewFamilies,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): FamiliesView = settlements.families(tripId, command, principal.userId)
}
