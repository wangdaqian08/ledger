package app.ledger.server.auth

import app.ledger.server.identity.IdentityProvider
import app.ledger.server.me.MeView
import app.ledger.server.me.toMeView
import app.ledger.server.user.UserDirectory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class SignInRequest(
    @field:NotBlank
    val idToken: String,
)

@RestController
@RequestMapping("/api/auth/session")
@Validated
class AuthController(
    private val identityProvider: IdentityProvider,
    private val users: UserDirectory,
    private val securityContextRepository: SecurityContextRepository,
) {
    @PostMapping
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

        return user.toMeView()
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun signOut(httpRequest: HttpServletRequest) {
        httpRequest.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
    }
}
