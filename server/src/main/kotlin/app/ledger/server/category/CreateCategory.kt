package app.ledger.server.category

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateCategory(
    @field:NotBlank
    @field:Size(max = 40)
    val name: String,
    /** One of the vendored Lucide slugs. The rule is no emoji, ever. */
    @field:NotBlank
    val icon: String,
    @field:Min(1)
    @field:Max(8)
    val hue: Int,
)
