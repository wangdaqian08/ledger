package app.ledger.server.identity

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Production's interim sign-in, until Google lands at build order step 10: a name is the whole
 * identity, exactly like dev's mock — but as its own deliberate opt-in profile, so the rule that
 * [MockIdentityProvider] can never *accidentally* stand in front of a real deployment holds.
 * Activating `name-signin` is the owner choosing, eyes open, that anyone who reaches the site can
 * sign in as any name. Acceptable among friends splitting a bill; nothing more.
 *
 * The provider string is `name`, never `mock`: `users` is unique on (provider, subject), so these
 * rows stay a distinct, honest record of how each identity was vouched for — and the Google
 * identities of step 10 will not silently merge with them (a linking pass is future work).
 */
@Component
@Profile("name-signin")
class NameIdentityProvider : IdentityProvider {
    override fun verify(token: String): ExternalIdentity {
        val name = token.trim()
        if (name.isEmpty()) throw InvalidIdentityToken("Sign-in needs a name")

        val subject = name.lowercase()
        return ExternalIdentity(
            provider = PROVIDER,
            subject = subject,
            // .invalid is reserved (RFC 2606): this can never be a deliverable address by accident.
            email = "${subject.replace(NON_EMAIL_CHARS, ".")}@name.invalid",
            displayName = name,
            photoUrl = null,
        )
    }

    private companion object {
        const val PROVIDER = "name"
        val NON_EMAIL_CHARS = Regex("[^a-z0-9]+")
    }
}
