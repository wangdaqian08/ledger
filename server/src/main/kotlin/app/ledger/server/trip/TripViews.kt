package app.ledger.server.trip

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The API contract is the demo's shape, not the database's (spec §4, Assembler). Entities are
 * never returned directly — that would weld the JSON to the schema and make either one hard to
 * change without breaking the other.
 */
data class MemberView(
    val id: UUID,
    val displayName: String,
    val personHue: Short,
    /** False until somebody signs in and claims this name. Such members still take a full share. */
    val claimed: Boolean,
    /**
     * The name is pinned because Jackson strips the `is` prefix from a Kotlin Boolean property and
     * would otherwise put this on the wire as `you`.
     */
    @get:JsonProperty("isYou")
    val isYou: Boolean,
)

data class TripView(
    val id: UUID,
    val name: String,
    val icon: String,
    val hue: Short,
    val currencyCode: String,
    val startsOn: LocalDate?,
    val endsOn: LocalDate?,
    val members: List<MemberView>,
    /** Positive means the group owes you. Derived by the engine, never stored. */
    val yourNetMinor: Long,
)

/** GroupsHome: every group, plus the two figures across all of them that the header shows. */
data class TripsView(
    val trips: List<TripView>,
    val overallNetMinor: Long,
    val settledTripCount: Int,
)

data class InviteView(val token: String, val expiresAt: Instant)
