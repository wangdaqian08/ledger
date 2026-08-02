package app.ledger.server.identity

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Log in as anybody by typing their name. The "token" is the display name, nothing more.
 *
 * Restricted to the dev profile so it can never be the thing standing between a real deployment
 * and a real user. Its one real job beyond convenience is that [ExternalIdentity.subject] is
 * derived from the name, so signing in twice as "Bob" lands on the same `users` row — without
 * that, dev data would sprout a new Bob on every login and no trip would hold together.
 */
@Component
@Profile("dev")
class MockIdentityProvider : IdentityProvider {
    override fun verify(token: String): ExternalIdentity {
        val name = token.trim()
        if (name.isEmpty()) throw InvalidIdentityToken("Mock sign-in needs a name")

        val subject = name.lowercase()
        return ExternalIdentity(
            provider = PROVIDER,
            subject = subject,
            email = "${subject.replace(NON_EMAIL_CHARS, ".")}@ledger.test",
            displayName = name,
            photoUrl = null,
        )
    }

    private companion object {
        const val PROVIDER = "mock"
        val NON_EMAIL_CHARS = Regex("[^a-z0-9]+")
    }
}
