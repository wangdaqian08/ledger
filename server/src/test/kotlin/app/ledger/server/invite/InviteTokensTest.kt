package app.ledger.server.invite

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * No Spring and no database: signing is a pure function of the secret and the clock, and forging
 * attempts are much easier to state directly than through HTTP.
 */
class InviteTokensTest {
    private val secret = "a-test-secret-long-enough-to-pass-validation"
    private val now = Instant.parse("2026-08-02T10:00:00Z")

    private fun tokensAt(instant: Instant, secret: String = this.secret) =
        InviteTokens(
            InviteProperties(secret = secret, validity = Duration.ofDays(14)),
            Clock.fixed(instant, ZoneOffset.UTC),
        )

    @Test
    fun `a freshly issued token admits the holder to its own trip`() {
        val trip = UUID.randomUUID()
        val tokens = tokensAt(now)

        assertEquals(trip, tokens.verify(tokens.issue(trip).token))
    }

    @Test
    fun `a token is refused the moment it expires`() {
        val tokens = tokensAt(now)
        val issued = tokens.issue(UUID.randomUUID())

        // Exactly at the expiry instant, not merely after it — an off-by-one here is a link that
        // outlives its own advertised lifetime.
        val atExpiry = tokensAt(issued.expiresAt)

        assertFailsWith<InvalidInviteToken> { atExpiry.verify(issued.token) }
    }

    @Test
    fun `a token still works a moment before it expires`() {
        val tokens = tokensAt(now)
        val issued = tokens.issue(UUID.randomUUID())

        val justBefore = tokensAt(issued.expiresAt.minusSeconds(1))

        justBefore.verify(issued.token)
    }

    @Test
    fun `the trip cannot be swapped without the signature failing`() {
        val tokens = tokensAt(now)
        val issued = tokens.issue(UUID.randomUUID())
        val (payload, signature) = issued.token.split(".")

        // Re-point the token at a trip of the attacker's choosing, keeping the original signature.
        val decoded = String(Base64.getUrlDecoder().decode(payload))
        val forgedPayload = decoded.replaceBefore(':', UUID.randomUUID().toString())
        val forged = "${Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.toByteArray())}.$signature"

        assertFailsWith<InvalidInviteToken> { tokens.verify(forged) }
    }

    @Test
    fun `the expiry cannot be extended without the signature failing`() {
        val tokens = tokensAt(now)
        val issued = tokens.issue(UUID.randomUUID())
        val (payload, signature) = issued.token.split(".")

        val decoded = String(Base64.getUrlDecoder().decode(payload))
        val extended = decoded.replaceAfterLast(':', now.plus(Duration.ofDays(3650)).epochSecond.toString())
        val forged = "${Base64.getUrlEncoder().withoutPadding().encodeToString(extended.toByteArray())}.$signature"

        assertFailsWith<InvalidInviteToken> { tokens.verify(forged) }
    }

    @Test
    fun `a token signed with another secret is refused`() {
        // The rotation story: changing the secret invalidates every outstanding link, which is the
        // only revocation a stateless token has.
        val issued = tokensAt(now, secret = "the-previous-secret-also-long-enough-ok").issue(UUID.randomUUID())

        assertFailsWith<InvalidInviteToken> { tokensAt(now).verify(issued.token) }
    }

    @Test
    fun `rubbish is refused rather than crashing`() {
        val tokens = tokensAt(now)

        listOf("", ".", "not-a-token", "a.b.c", "!!!.???", "${"x".repeat(500)}.y").forEach {
            assertFailsWith<InvalidInviteToken>("accepted or crashed on: $it") { tokens.verify(it) }
        }
    }
}
