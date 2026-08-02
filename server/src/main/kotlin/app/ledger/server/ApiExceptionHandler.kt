package app.ledger.server

import app.ledger.server.identity.InvalidIdentityToken
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    /**
     * 401, not 400. A token the provider will not vouch for is a failure to authenticate, and the
     * SPA reacts to 401 by showing the sign-in screen.
     */
    @ExceptionHandler(InvalidIdentityToken::class)
    fun invalidToken(e: InvalidIdentityToken): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.message ?: "Sign-in failed")
}
