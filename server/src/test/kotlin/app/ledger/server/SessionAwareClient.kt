package app.ledger.server

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * A browser's worth of behaviour and no more: it keeps the cookies a response sets, sends them
 * back, and echoes the CSRF cookie in the header Spring Security expects.
 *
 * Worth the fifty lines rather than using MockMvc or RestTestClient, because the thing under test
 * is that a session *round-trips through Postgres* — that only means something over real HTTP with
 * real cookies, and it needs a client that will hand back a 401 instead of throwing on it.
 */
class SessionAwareClient(baseUrl: String) {
    private val cookies = linkedMapOf<String, String>()

    private val http = RestClient
        .builder()
        .baseUrl(baseUrl)
        // Named rather than left to auto-detection: the factory Spring falls back to when no HTTP
        // client library is on the path cannot send PATCH at all, which would fail the people-list
        // tests for a reason that has nothing to do with the application.
        .requestFactory(JdkClientHttpRequestFactory())
        // Every status is the test's business, including the 401s and 403s that are the point.
        .defaultStatusHandler({ true }, { _, _ -> })
        .build()

    fun get(path: String): ResponseEntity<String> = exchange(HttpMethod.GET, path, null)

    fun post(path: String, body: Any): ResponseEntity<String> = exchange(HttpMethod.POST, path, body)

    fun patch(path: String, body: Any): ResponseEntity<String> = exchange(HttpMethod.PATCH, path, body)

    fun delete(path: String): ResponseEntity<String> = exchange(HttpMethod.DELETE, path, null)

    fun cookie(name: String): String? = cookies[name]

    private fun exchange(method: HttpMethod, path: String, body: Any?): ResponseEntity<String> {
        var spec = http.method(method).uri(path).contentType(MediaType.APPLICATION_JSON)

        if (cookies.isNotEmpty()) {
            spec = spec.header(HttpHeaders.COOKIE, cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
        cookies[CSRF_COOKIE]?.let { spec = spec.header(CSRF_HEADER, it) }
        if (body != null) spec = spec.body(body)

        val response = spec.retrieve().toEntity(String::class.java)
        response.headers[HttpHeaders.SET_COOKIE].orEmpty().forEach(::remember)
        return response
    }

    private fun remember(setCookie: String) {
        val attributes = setCookie.split(";").map(String::trim)
        val (name, value) = attributes
            .first()
            .split("=", limit = 2)
            .let { it[0] to it.getOrElse(1) { "" } }

        // Sign-out clears the session by sending it back empty with Max-Age=0. Treating that as a
        // value would leave the client "holding" a dead session and hide the bug it is testing for.
        val cleared = value.isEmpty() || attributes.any { it.equals("Max-Age=0", ignoreCase = true) }
        if (cleared) cookies.remove(name) else cookies[name] = value
    }

    companion object {
        const val SESSION_COOKIE = "LEDGER_SESSION"
        const val CSRF_COOKIE = "LEDGER-XSRF"
        const val CSRF_HEADER = "X-XSRF-TOKEN"
    }
}
