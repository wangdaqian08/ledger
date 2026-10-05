package app.ledger.server.user

import app.ledger.server.identity.ExternalIdentity
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Turns a verified [ExternalIdentity] into the `users` row it belongs to, creating it the first
 * time. Sign-in is the only place a user is created — there is no registration step.
 *
 * Also the one writer of a person's PayID, the only thing on the row that is theirs to set rather
 * than the identity provider's.
 */
@Service
class UserDirectory(private val users: UserRepository, private val clock: Clock) {
    /**
     * Two concurrent first sign-ins by the same person would both miss the lookup and both insert;
     * `users_provider_subject_unique` turns the loser into a constraint violation rather than a
     * duplicate person. That is the correct outcome, and rare enough not to be worth retrying.
     *
     * Never touches the PayID or its timestamp: a sign-in that re-stamped it would light the
     * "updated recently" badge every time somebody opened the app, and teach everyone to ignore it.
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

    /**
     * Sets [userId]'s own PayID — there is no way to name anybody else's, which is the whole
     * permission rule. Trimmed, and blank means clearing it. Never format-checked: a phone number,
     * an email, an ABN or a BSB and account all appear as PayIDs, and the app never pays one — it
     * only shows it for somebody to copy.
     *
     * The length is checked here, after trimming, rather than by a request annotation that would
     * count the whitespace about to be thrown away. Characters are counted as code points, the way
     * the `users_pay_id_length` CHECK's `char_length` counts them.
     *
     * The timestamp moves only when the stored value does — clearing included, re-saving the same
     * PayID not — because it drives the badge described on [PAY_ID_RECENTLY_CHANGED].
     */
    @Transactional
    fun setPayId(userId: UUID, raw: String?): UserEntity {
        val payId = raw?.trim()?.ifEmpty { null }
        val length = payId?.let { it.codePointCount(0, it.length) } ?: 0
        if (length > MAX_PAY_ID_LENGTH) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "a PayID is limited to $MAX_PAY_ID_LENGTH characters, got $length",
            )
        }

        val user = users.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No such user")
        }
        if (user.payId != payId) {
            user.payId = payId
            user.payIdUpdatedAt = clock.instant()
        }
        return user
    }

    /** Whether [user]'s PayID changed within [PAY_ID_RECENTLY_CHANGED] of now. */
    fun payIdChangedRecently(user: UserEntity): Boolean {
        val changedAt = user.payIdUpdatedAt ?: return false
        return changedAt > clock.instant().minus(PAY_ID_RECENTLY_CHANGED)
    }

    companion object {
        /** Mirrors the `users_pay_id_length` CHECK in V7. */
        const val MAX_PAY_ID_LENGTH = 256

        /**
         * How long everyone else on a trip sees "updated recently" beside a PayID after it changes.
         *
         * Under the `name-signin` profile a name is an identity, so anybody can sign in as anybody
         * and swap their PayID for one of their own — and the next person to pay would send real
         * money to it. Nothing here can prevent that without real sign-in; a week-long badge at
         * least means a changed PayID is noticed by the people about to use it, and a week covers
         * the settle-up that typically follows a trip.
         */
        val PAY_ID_RECENTLY_CHANGED: Duration = Duration.ofDays(7)
    }
}
