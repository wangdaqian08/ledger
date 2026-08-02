package app.ledger.server.invite

import jakarta.validation.constraints.Size
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * There is no default secret, deliberately. `application-dev.yaml` supplies one for local work and
 * the tests; every other profile must be given one or the application refuses to start. A shipped
 * default is a signing key that is public the moment the repository is, and the only reliable way
 * to prevent one reaching production is for it not to exist.
 */
@ConfigurationProperties("ledger.invite")
@Validated
data class InviteProperties(
    /** 32 characters is 256 bits at one byte each — the width of the HMAC this keys. */
    @field:Size(min = 32, message = "the invite signing secret must be at least 32 characters")
    val secret: String,
    val validity: Duration = Duration.ofDays(14),
)

class InvalidInviteToken(message: String) : RuntimeException(message)

/**
 * The share link's token: a trip id and an expiry, signed so neither can be edited.
 *
 * Stateless by choice (spec §4). The trade-off to keep in mind is that a token cannot be withdrawn
 * before it expires — if a link leaks, the only remedies are waiting it out or rotating the secret,
 * which invalidates every outstanding link at once. That is why [InviteProperties.validity] is
 * measured in days rather than months.
 */
@Component
class InviteTokens(
    private val properties: InviteProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun issue(tripId: UUID): IssuedInvite {
        val expiresAt = clock.instant().plus(properties.validity)
        val payload = "$tripId:${expiresAt.epochSecond}"
        val token = "${payload.base64()}.${sign(payload).base64()}"
        return IssuedInvite(token = token, expiresAt = expiresAt)
    }

    /** @return the trip the token admits the holder to. */
    fun verify(token: String): UUID {
        val parts = token.split(".")
        if (parts.size != 2) throw InvalidInviteToken("Malformed invite link")

        val payload = decode(parts[0])
        val signature = decodeBytes(parts[1])

        // Constant-time: a byte-by-byte comparison that returns early leaks how much of a guessed
        // signature was right, which is enough to forge one a byte at a time.
        if (!MessageDigest.isEqual(sign(payload), signature)) throw InvalidInviteToken("Invalid invite link")

        // Only after the signature is trusted is the payload worth reading.
        val separator = payload.lastIndexOf(':')
        if (separator < 0) throw InvalidInviteToken("Invalid invite link")

        val expiresAt = payload
            .substring(separator + 1)
            .toLongOrNull()
            ?.let(Instant::ofEpochSecond)
            ?: throw InvalidInviteToken("Invalid invite link")
        if (!expiresAt.isAfter(clock.instant())) throw InvalidInviteToken("This invite link has expired")

        return runCatching { UUID.fromString(payload.substring(0, separator)) }
            .getOrElse { throw InvalidInviteToken("Invalid invite link") }
    }

    private fun sign(payload: String): ByteArray {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(properties.secret.toByteArray(), ALGORITHM))
        return mac.doFinal(payload.toByteArray())
    }

    private fun String.base64(): String = toByteArray().base64()

    private fun ByteArray.base64(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)

    private fun decode(value: String): String = String(decodeBytes(value))

    private fun decodeBytes(value: String): ByteArray =
        runCatching { Base64.getUrlDecoder().decode(value) }
            .getOrElse { throw InvalidInviteToken("Malformed invite link") }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
    }
}

data class IssuedInvite(val token: String, val expiresAt: Instant)
