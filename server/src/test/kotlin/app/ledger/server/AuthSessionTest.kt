package app.ledger.server

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sign-in round trip over real HTTP, against real Postgres.
 *
 * Every test starts with an unauthenticated `GET /api/me`. That is not ceremony — it is the actual
 * first request a browser makes, and the response is what carries the CSRF cookie the SPA needs
 * before it can post anything. If that stopped working, sign-in would be impossible for a real
 * client while every mock-based test still passed.
 */
class AuthSessionTest : PostgresTest() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private fun client() = SessionAwareClient("http://localhost:$port")

    private fun signIn(client: SessionAwareClient, name: String) =
        client.post("/api/auth/session", mapOf("idToken" to name))

    @Test
    fun `me is refused before signing in`() {
        val response = client().get("/api/me")

        // 401, not a redirect to a login page that does not exist — the SPA reads this as
        // "show the sign-in screen".
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `the first response carries a csrf token even though it is refused`() {
        val client = client()

        client.get("/api/me")

        assertNotNull(client.cookie(SessionAwareClient.CSRF_COOKIE), "no CSRF cookie to sign in with")
    }

    @Test
    fun `signing in and then asking who I am`() {
        val client = client()
        client.get("/api/me")

        val signIn = signIn(client, "Bob")
        assertEquals(HttpStatus.OK, signIn.statusCode)
        assertNotNull(client.cookie(SessionAwareClient.SESSION_COOKIE), "sign-in set no session cookie")

        val me = client.get("/api/me")
        assertEquals(HttpStatus.OK, me.statusCode)
        assertTrue(me.body!!.contains("\"displayName\":\"Bob\""), "unexpected /api/me body: ${me.body}")
    }

    @Test
    fun `the session is stored in postgres, not in memory`() {
        // This is the reason Spring Session JDBC is here at all: on Cloud Run the instance that
        // serves the next request is not necessarily the one that signed the user in.
        val client = client()
        client.get("/api/me")
        signIn(client, "Wendy")

        val sessions = jdbc.queryForObject(
            "SELECT count(*) FROM spring_session WHERE principal_name IS NOT NULL",
            Int::class.java,
        )

        assertTrue(sessions!! > 0, "no session row reached the database")
    }

    @Test
    fun `a refused request leaves no session behind`() {
        // Anonymous traffic must cost nothing. Spring's default request cache would open a session
        // for every refused request so it could redirect back after login — with no login page to
        // redirect to, that is just a row in Postgres per crawler.
        val before = sessionCount()

        client().get("/api/me")

        assertEquals(before, sessionCount(), "an unauthenticated request created a session")
    }

    @Test
    fun `signing out ends the session`() {
        val client = client()
        client.get("/api/me")
        signIn(client, "Carol")

        val signOut = client.delete("/api/auth/session")
        assertEquals(HttpStatus.NO_CONTENT, signOut.statusCode)
        assertNull(client.cookie(SessionAwareClient.SESSION_COOKIE), "the session cookie outlived sign-out")

        assertEquals(HttpStatus.UNAUTHORIZED, client.get("/api/me").statusCode)
    }

    @Test
    fun `a write without a csrf token is rejected`() {
        // No prior GET, so this client holds no token — exactly the position a cross-site form
        // is in. Sign-in is the one endpoint that permits anonymous callers, so if CSRF were off
        // this would succeed and an attacker could log a victim into an account of their choosing.
        val client = client()

        val response = client.post("/api/auth/session", mapOf("idToken" to "Mallory"))

        // 401 rather than the 403 you might expect: the caller is anonymous, and Spring Security
        // turns an access denial for an anonymous request into "authenticate first" via the entry
        // point. Either way the request was refused — which is what the next two lines prove,
        // because a status code alone would not distinguish "blocked" from "blocked too late".
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertNull(client.cookie(SessionAwareClient.SESSION_COOKIE), "a rejected sign-in still opened a session")
        assertEquals(
            0,
            jdbc.queryForObject("SELECT count(*) FROM users WHERE display_name = 'Mallory'", Int::class.java),
            "a rejected sign-in still created the user",
        )
    }

    @Test
    fun `signing in twice as the same name is the same user`() {
        val first = client().also { it.get("/api/me") }
        val second = client().also { it.get("/api/me") }

        val firstId = idFrom(signIn(first, "Repeat Visitor").body!!)
        val secondId = idFrom(signIn(second, "repeat visitor").body!!)

        assertEquals(firstId, secondId, "the same person signed in twice and became two users")
    }

    @Test
    fun `signing in mints a new session rather than adopting the one already in the browser`() {
        val client = client()
        client.get("/api/me")
        signIn(client, "Dave")
        val beforeSecondSignIn = client.cookie(SessionAwareClient.SESSION_COOKIE)

        signIn(client, "Dave")

        // A session id that survives sign-in is a session an attacker could have planted first.
        assertTrue(
            beforeSecondSignIn != client.cookie(SessionAwareClient.SESSION_COOKIE),
            "the session id was reused across sign-in",
        )
    }

    @Test
    fun `the csrf token is replaced at sign-in`() {
        val client = client()
        client.get("/api/me")
        val beforeSignIn = client.cookie(SessionAwareClient.CSRF_COOKIE)

        signIn(client, "Trudy")

        // Both halves matter. The token lives in a cookie, not the session, so invalidating the
        // session does not touch it — a token planted in the browser before sign-in must not still
        // be good afterwards. But "changed" is also satisfied by "cleared and never replaced",
        // which would leave the SPA unable to write anything at all; assert a real replacement.
        val afterSignIn = client.cookie(SessionAwareClient.CSRF_COOKIE)
        assertNotNull(afterSignIn, "sign-in cleared the CSRF token without issuing a new one")
        assertNotEquals(beforeSignIn, afterSignIn, "the pre-sign-in CSRF token survived authentication")
    }

    @Test
    fun `signing out everywhere ends the session on the other device too`() {
        // Two clients, one person: a phone and a laptop, each with its own session.
        val phone = client().also { it.get("/api/me") }
        val laptop = client().also { it.get("/api/me") }
        signIn(phone, "Frankie")
        signIn(laptop, "Frankie")
        assertEquals(HttpStatus.OK, laptop.get("/api/me").statusCode, "the second sign-in did not take")

        val response = phone.delete("/api/auth/sessions")

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertEquals(
            HttpStatus.UNAUTHORIZED,
            laptop.get("/api/me").statusCode,
            "the lost device is still signed in",
        )
        assertEquals(HttpStatus.UNAUTHORIZED, phone.get("/api/me").statusCode)
    }

    @Test
    fun `signing out everywhere leaves other people alone`() {
        // The revocation is keyed on the principal name, so getting it wrong would sign out the
        // entire application rather than one person.
        val gilbert = client().also { it.get("/api/me") }
        val hana = client().also { it.get("/api/me") }
        signIn(gilbert, "Gilbert")
        signIn(hana, "Hana")

        gilbert.delete("/api/auth/sessions")

        assertEquals(HttpStatus.OK, hana.get("/api/me").statusCode, "somebody else was signed out too")
    }

    @Test
    fun `an empty name is not an identity`() {
        val client = client()
        client.get("/api/me")

        assertEquals(HttpStatus.UNAUTHORIZED, signIn(client, "   ").statusCode)
    }

    private fun sessionCount(): Int =
        jdbc.queryForObject("SELECT count(*) FROM spring_session", Int::class.java)!!

    private fun idFrom(body: String): String =
        Regex("\"id\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: error("no id in $body")
}
