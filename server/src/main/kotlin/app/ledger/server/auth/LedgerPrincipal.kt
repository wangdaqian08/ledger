package app.ledger.server.auth

import java.io.Serializable
import java.security.Principal
import java.util.UUID

/**
 * The signed-in user, as carried in the session.
 *
 * [Serializable] is not decoration: sessions live in Postgres (`SPRING_SESSION_ATTRIBUTES`), so the
 * security context is serialised on every write. A non-serialisable principal fails at runtime on
 * the first request, not at compile time.
 *
 * [getName] returns the id rather than the display name because Spring Session copies it into
 * `SPRING_SESSION.PRINCIPAL_NAME`, which is `VARCHAR(100)` — a uuid always fits, a name might not.
 */
data class LedgerPrincipal(
    val userId: UUID,
    val displayName: String,
) : Principal, Serializable {
    override fun getName(): String = userId.toString()

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
