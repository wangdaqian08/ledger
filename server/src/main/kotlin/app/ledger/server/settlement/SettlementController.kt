package app.ledger.server.settlement

import app.ledger.server.auth.LedgerPrincipal
import app.ledger.server.payback.PaybackView
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

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
