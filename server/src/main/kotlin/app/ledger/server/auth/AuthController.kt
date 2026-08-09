package app.ledger.server.auth

import app.ledger.server.identity.IdentityProvider
import app.ledger.server.me.MeView
import app.ledger.server.me.toMeView
import app.ledger.server.user.UserDirectory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestHandler
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
class AuthController(
    private val identityProvider: IdentityProvider,
    private val users: UserDirectory,
    private val securityContextRepository: SecurityContextRepository,
    private val sessions: FindByIndexNameSessionRepository<out Session>,
    csrfTokenRepository: CsrfTokenRepository,
    csrfTokenRequestHandler: CsrfTokenRequestHandler,
) {
    /**
     * Spring applies this automatically for form login. Sign-in here is hand-rolled, so it has to
     * be invoked by hand too — see [signIn].
     *
     * The handler must be set explicitly. Rotation clears the old cookie and then writes a
     * replacement, and the default handler is deferred — it would do the clearing, decline to
     * materialise the new token because nothing asked for it, and leave the browser holding no CSRF
     * token at all. Every subsequent write would then be rejected.
     */
    private val csrfRotation = CsrfAuthenticationStrategy(csrfTokenRepository).apply {
        setRequestHandler(csrfTokenRequestHandler)
    }

    @PostMapping("/api/auth/session")
    fun signIn(
        @RequestBody request: SignInRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): MeView {
        val identity = identityProvider.verify(request.idToken)
        val user = users.signIn(identity)

        // Drop any existing session before minting the new one. Without this, a session id an
        // attacker planted in the browser survives sign-in and becomes an authenticated session.
        httpRequest.getSession(false)?.invalidate()

        val authentication = UsernamePasswordAuthenticationToken(
            LedgerPrincipal(user.id, user.displayName),
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)

        httpRequest.getSession(true)
        // Spring Security stopped saving the context implicitly in 6.0; the session row in Postgres
        // is written by this call.
        securityContextRepository.saveContext(context, httpRequest, httpResponse)

        // Replace the CSRF token as well as the session id. The token lives in a cookie rather than
        // the session, so invalidating the session above does not touch it — without this it would
        // survive sign-in, and a token an attacker handed the browser beforehand would still be
        // valid afterwards.
        csrfRotation.onAuthentication(authentication, httpRequest, httpResponse)

        return user.toMeView()
    }

    @DeleteMapping("/api/auth/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun signOut(httpRequest: HttpServletRequest) {
        httpRequest.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
    }

    /**
     * Sign out everywhere: drops every session this user has, on every device, not just the one
     * holding the cookie that made the request.
     *
     * Sessions last 30 days, which is the right feel for something used on a phone during a trip —
     * but a 30-day session with no way to end it from the server is a lost handset that stays
     * signed in until the trip is long over. `SPRING_SESSION.PRINCIPAL_NAME` is indexed for exactly
     * this lookup, which is why [LedgerPrincipal.getName] returns the user id.
     */
    @DeleteMapping("/api/auth/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun signOutEverywhere(
        @AuthenticationPrincipal principal: LedgerPrincipal,
        httpRequest: HttpServletRequest,
    ) {
        sessions.findByPrincipalName(principal.name).keys.forEach(sessions::deleteById)

        // The current session is among those just deleted, but the servlet container is still
        // holding it; invalidating here stops it being written back on the way out.
        httpRequest.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
    }
}
