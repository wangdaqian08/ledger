package app.ledger.server.me

import app.ledger.server.auth.LedgerPrincipal
import app.ledger.server.user.UserEntity
import app.ledger.server.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * What the "You" screen renders.
 *
 * [friends] is in the contract from the start even though it is always empty today: the screen
 * lists friends with a shared-group count (spec §6), and that needs trips, which arrive at build
 * order step 4. Shipping the field now means the client's shape never has to change.
 */
data class MeView(
    val id: UUID,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val friends: List<FriendView> = emptyList(),
)

data class FriendView(
    val id: UUID,
    val displayName: String,
    val photoUrl: String?,
    val sharedTripCount: Int,
)

fun UserEntity.toMeView(): MeView = MeView(
    id = id,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl,
)

@RestController
class MeController(private val users: UserRepository) {
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
}
