package app.ledger.server.auth

import jakarta.validation.constraints.NotBlank

data class SignInRequest(
    @field:NotBlank
    val idToken: String,
)
