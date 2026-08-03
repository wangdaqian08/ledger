package app.ledger.server.payback

import app.ledger.server.auth.LedgerPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class PaybackController(private val paybacks: PaybackService) {
    @PostMapping("/api/items/{itemId}/paybacks")
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(
        @PathVariable itemId: UUID,
        @Valid @RequestBody command: SubmitPayback,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): PaybackView = paybacks.submitForItem(itemId, command, principal.userId)

    @PostMapping("/api/paybacks/{paybackId}/approve")
    fun approve(
        @PathVariable paybackId: UUID,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): PaybackView = paybacks.approve(paybackId, principal.userId)

    @PostMapping("/api/paybacks/{paybackId}/reject")
    fun reject(
        @PathVariable paybackId: UUID,
        @Valid @RequestBody command: RejectPayback,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): PaybackView = paybacks.reject(paybackId, command, principal.userId)

    /** Correcting a rejected claim, which sends it back for review. */
    @PatchMapping("/api/paybacks/{paybackId}")
    fun resubmit(
        @PathVariable paybackId: UUID,
        @Valid @RequestBody command: ResubmitPayback,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): PaybackView = paybacks.resubmit(paybackId, command, principal.userId)

    @PostMapping("/api/paybacks/{paybackId}/undo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun undo(
        @PathVariable paybackId: UUID,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ) = paybacks.undo(paybackId, principal.userId)
}
