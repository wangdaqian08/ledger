package app.ledger.server.trip

import app.ledger.server.auth.LedgerPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/trips")
class TripController(private val trips: TripService) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): TripsView = trips.listFor(principal.userId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody command: CreateTrip,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): TripView = trips.create(command, principal.userId)

    @GetMapping("/{tripId}")
    fun detail(
        @PathVariable tripId: UUID,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): TripView = trips.detail(tripId, principal.userId)

    @PostMapping("/{tripId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMember(
        @PathVariable tripId: UUID,
        @Valid @RequestBody command: AddMember,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): MemberView = trips.addMember(tripId, command, principal.userId)

    @PatchMapping("/{tripId}/members/{memberId}")
    fun renameMember(
        @PathVariable tripId: UUID,
        @PathVariable memberId: UUID,
        @Valid @RequestBody command: RenameMember,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): MemberView = trips.renameMember(tripId, memberId, command, principal.userId)

    @PostMapping("/{tripId}/invite")
    fun invite(
        @PathVariable tripId: UUID,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): InviteView = trips.invite(tripId, principal.userId).let { InviteView(it.token, it.expiresAt) }

    @PostMapping("/{tripId}/claim")
    fun claim(
        @PathVariable tripId: UUID,
        @Valid @RequestBody command: ClaimMember,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): TripView = trips.claim(tripId, command, principal.userId)

    @PostMapping("/{tripId}/claimable")
    fun claimable(
        @PathVariable tripId: UUID,
        @Valid @RequestBody command: PreviewInvite,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): ClaimableView = trips.claimable(tripId, command.token, principal.userId)
}
