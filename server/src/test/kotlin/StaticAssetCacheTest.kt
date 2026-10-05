package app.ledger.server

import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins [app.ledger.server.spa.StaticResourceConfig]: the content-hashed bundle under /assets must be
 * served cacheable-and-immutable, not with Spring Security's default `no-store`. Without the resource
 * handler's explicit Cache-Control the security filter chain fills the gap and every page load
 * re-fetches the whole bundle through the JVM — the exact regression this guards against.
 *
 * `probe.js` lives under `src/test/resources/static/assets/`, so it resolves through the same
 * `classpath:/static/assets/` handler the real bundle uses.
 */
class StaticAssetCacheTest : ApiTest() {
    private fun client() = SessionAwareClient("http://localhost:$port")

    @Test
    fun `hashed assets are served immutable, not no-store`() {
        val response = client().get("/assets/probe.js")

        assertEquals(HttpStatus.OK, response.statusCode, "probe asset should be served")
        val cacheControl = response.headers.cacheControl
        assertNotNull(cacheControl, "the asset must carry a Cache-Control header")
        assertTrue("immutable" in cacheControl, "expected immutable, got: $cacheControl")
        assertTrue("max-age=31536000" in cacheControl, "expected a one-year max-age, got: $cacheControl")
        assertTrue("public" in cacheControl, "expected public, got: $cacheControl")
        assertFalse("no-store" in cacheControl, "Spring Security's no-store must not win here: $cacheControl")
    }

    @Test
    fun `the security default still applies off the asset path`() {
        // Scoping guard: the immutable header is only for /assets/**. A closed API path must still
        // receive Spring Security's no-store, or the change has weakened caching protection app-wide.
        val response = client().get("/api/trips")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        val cacheControl = response.headers.cacheControl
        assertNotNull(cacheControl)
        assertTrue("no-store" in cacheControl, "expected Security's no-store off the asset path: $cacheControl")
    }
}
