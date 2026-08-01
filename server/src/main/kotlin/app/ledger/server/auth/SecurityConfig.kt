package app.ledger.server.auth

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.savedrequest.NullRequestCache

/**
 * Everything is closed except signing in.
 *
 * The app is served from one origin (spec §4), so there is no CORS here and no token ever reaches
 * JavaScript — the session id travels in an `HttpOnly`, `SameSite=Lax` cookie configured in
 * `application.yaml`, and the session itself lives in Postgres so a second Cloud Run instance can
 * serve a user the first one signed in.
 */
@Configuration
class SecurityConfig {
    /**
     * Explicit rather than left to Spring's default, because [AuthController] has to save the
     * context by hand when it mints a session, and both sides must agree on where it is kept.
     */
    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        securityContextRepository: SecurityContextRepository,
    ): SecurityFilterChain {
        val csrfTokenHandler = CsrfTokenRequestAttributeHandler()
        // Opt out of deferred token loading. Deferred is the faster default, but it only writes the
        // XSRF-TOKEN cookie once something asks for the token — and with a JSON API nothing ever
        // does, so the SPA would have no token to send and every write would 403. Resolving eagerly
        // means any response, including the 401 the SPA gets before signing in, carries the cookie.
        csrfTokenHandler.setCsrfRequestAttributeName(null)

        // One statement per concern rather than one long chain, matching Spring's own reference
        // configuration. Each of these is a separate decision and reads better argued separately.
        http.authorizeHttpRequests {
            it.requestMatchers(HttpMethod.POST, "/api/auth/session").permitAll()
            it.anyRequest().authenticated()
        }

        http.csrf {
            // withHttpOnlyFalse is required, not sloppy: the SPA has to read this cookie to echo
            // it back in the X-XSRF-TOKEN header. It is the CSRF token, not the session.
            it.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            it.csrfTokenRequestHandler(csrfTokenHandler)
        }

        http.securityContext { it.securityContextRepository(securityContextRepository) }

        http.exceptionHandling {
            // Without this a signed-out request gets a redirect to a login page that does not
            // exist. The SPA needs a plain 401 to know it should show the sign-in screen.
            it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
        }

        // The default request cache stashes the refused request in a new session so the user can be
        // sent back to it after logging in. There is no login redirect here, so all it would achieve
        // is a row in SPRING_SESSION for every unauthenticated request that ever reaches the app —
        // including from crawlers and probes.
        http.requestCache { it.requestCache(NullRequestCache()) }

        return http.build()
    }
}
