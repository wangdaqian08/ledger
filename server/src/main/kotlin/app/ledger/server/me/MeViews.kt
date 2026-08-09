package app.ledger.server.me

import app.ledger.server.user.UserEntity
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
