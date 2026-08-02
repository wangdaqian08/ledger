package app.ledger.server.user

import app.ledger.server.identity.ExternalIdentity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Turns a verified [ExternalIdentity] into the `users` row it belongs to, creating it the first
 * time. Sign-in is the only place a user is created — there is no registration step.
 */
@Service
class UserDirectory(private val users: UserRepository) {
    /**
     * Two concurrent first sign-ins by the same person would both miss the lookup and both insert;
     * `users_provider_subject_unique` turns the loser into a constraint violation rather than a
     * duplicate person. That is the correct outcome, and rare enough not to be worth retrying.
     */
    @Transactional
    fun signIn(identity: ExternalIdentity): UserEntity {
        val existing = users.findByProviderAndSubject(identity.provider, identity.subject)
        if (existing != null) {
            // The provider is the authority on these three, so a changed name or photo lands here.
            existing.email = identity.email
            existing.displayName = identity.displayName
            existing.photoUrl = identity.photoUrl
            return existing
        }

        return users.save(
            UserEntity(
                provider = identity.provider,
                subject = identity.subject,
                email = identity.email,
                displayName = identity.displayName,
                photoUrl = identity.photoUrl,
            ),
        )
    }
}
