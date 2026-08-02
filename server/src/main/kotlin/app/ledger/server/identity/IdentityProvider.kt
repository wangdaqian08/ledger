package app.ledger.server.identity

/**
 * Who a sign-in token says you are, in the only terms the rest of the app cares about.
 *
 * [provider] plus [subject] is the identity's permanent key — it is what `users` is unique on, and
 * it is what makes a second sign-in find the same row rather than mint a new person.
 */
data class ExternalIdentity(
    val provider: String,
    val subject: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?,
)

/**
 * The one seam between the app and whoever is vouching for the user.
 *
 * There are two implementations by design (spec §4): [MockIdentityProvider] on the dev profile so
 * development needs no Google credentials and no network, and a Google one at build order step 10.
 * Nothing above this interface knows which is in play.
 */
interface IdentityProvider {
    /** @throws InvalidIdentityToken if the token is not one this provider will vouch for. */
    fun verify(token: String): ExternalIdentity
}

class InvalidIdentityToken(message: String) : RuntimeException(message)
