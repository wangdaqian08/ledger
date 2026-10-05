package app.ledger.server.me

import app.ledger.server.auth.LedgerPrincipal
import app.ledger.server.user.UserDirectory
import app.ledger.server.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class MeController(private val users: UserRepository, private val directory: UserDirectory) {
    @GetMapping("/api/me")
    fun me(
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): MeView {
        // A session whose user has since been deleted is not a 401 — the caller is who they say
        // they are, there is simply nothing left to show.
        val user = users.findById(principal.userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No such user")
        }
        return user.toMeView()
    }

    @PutMapping("/api/me/pay-id")
    fun setPayId(
        @RequestBody command: SetPayId,
        @AuthenticationPrincipal principal: LedgerPrincipal
    ): MeView = directory.setPayId(principal.userId, command.payId).toMeView()
}
