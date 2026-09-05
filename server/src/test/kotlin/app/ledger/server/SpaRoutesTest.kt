package app.ledger.server

import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

/**
 * The jar carries the SPA (spec §4), so the server hands it out: the screen routes and the hashed
 * assets must answer a signed-out browser — that is where the sign-in screen comes from — while
 * the API stays shut. The files served here are placeholders in src/test/resources/static; the
 * real bundle is embedded by bootJar.
 */
class SpaRoutesTest : ApiTest() {
    private fun visitor() = SessionAwareClient("http://localhost:$port")

    @Test
    fun `every screen route hands a signed-out browser the app shell, uncached`() {
        for (path in listOf("/", "/signin", "/trips/${UUID.randomUUID()}", "/join/${UUID.randomUUID()}")) {
            val response = visitor().get(path)
            assertEquals(HttpStatus.OK, response.statusCode, "GET $path")
            assertTrue(response.body.orEmpty().contains("test placeholder"), "GET $path serves index.html")
            // The shell names the content-hashed bundle; a heuristically cached shell would pin a
            // browser to a bundle the next deploy deletes.
            assertEquals("no-cache", response.headers.getFirst("Cache-Control"), "GET $path")
        }
    }

    @Test
    fun `the hashed assets are open, the API is not, and there is no catch-all`() {
        assertEquals(HttpStatus.OK, visitor().get("/assets/app.js").statusCode)
        // Still a hard 401 for the numbers themselves — serving the shell opened nothing else.
        assertEquals(HttpStatus.UNAUTHORIZED, visitor().get("/api/me").statusCode)
        // Signed in, an unknown path is an honest 404, not the app shell: a catch-all would
        // 200-HTML every mistyped URL and crawler probe.
        assertEquals(HttpStatus.NOT_FOUND, signedIn("Probe").get("/nowhere").statusCode)
    }
}
