package app.ledger.server.trip

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

/**
 * The write side, one object per operation (spec §4). Each carries exactly one authorisation rule,
 * stated on the service method that handles it, so a permission question has one place to look
 * rather than being spread through a controller.
 */
data class CreateTrip(
    @field:NotBlank
    @field:Size(max = 80)
    val name: String,
    /** A Lucide slug. No emoji, ever. */
    @field:NotBlank
    val icon: String,
    @field:Min(1)
    @field:Max(8)
    val hue: Int,
    @field:Size(min = 3, max = 3)
    val currencyCode: String,
    val startsOn: LocalDate? = null,
    val endsOn: LocalDate? = null,
)

data class AddMember(
    @field:NotBlank
    @field:Size(max = 80)
    val displayName: String,
)

data class ClaimMember(
    @field:NotBlank
    val token: String,
    val memberId: UUID,
)

/** The share link asking which names are still free, before its holder is on the trip. */
data class PreviewInvite(
    @field:NotBlank
    val token: String,
)
