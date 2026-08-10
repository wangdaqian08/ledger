package app.ledger.server

import app.ledger.server.identity.InvalidIdentityToken
import app.ledger.server.invite.InvalidInviteToken
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.orm.ObjectOptimisticLockingFailureException
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

    /**
     * 400, not 403. The caller is signed in and entitled to ask; the link they were given is the
     * thing that is wrong, and the message says which way — expired reads differently from forged
     * to the person holding it, and neither tells them anything they could not already work out.
     */
    @ExceptionHandler(InvalidInviteToken::class)
    fun invalidInvite(e: InvalidInviteToken): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "Invalid invite link")

    /**
     * 409. Two edits to one expense started from the same state and this one lost the race — the
     * other landed first and moved the version on. Better a conflict the client can retry than a
     * silent overwrite that loses the first edit's people list.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun staleEdit(e: ObjectOptimisticLockingFailureException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "This expense was changed while you were editing it. Reopen it and try again.",
        )
}
