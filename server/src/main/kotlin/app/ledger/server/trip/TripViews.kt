package app.ledger.server.trip

import app.ledger.server.item.ItemView
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
    /**
     * Each item carries its splits, so tapping a row opens the detail sheet with no second
     * request — the demo's behaviour, and spec §6's one deliberate shape decision. Paybacks are
     * *not* here: they are unbounded per item and only the approval section needs them, so they
     * come from `GET /api/items/{id}`.
     */
    val items: List<ItemView>,
    /** Positive means the group owes you. Derived by the engine, never stored. */
    val yourNetMinor: Long,
    /** The three headline figures, derived by the engine here so no screen recomputes them. */
    val groupSpendMinor: Long,
    val yourShareMinor: Long,
    val youFrontedMinor: Long,
    /**
     * Whether the viewer created this trip. The creator can edit anyone's expense, approve any
     * payback and change the roster; without this the client could only discover that by attempting
     * a write and being refused.
     */
    val youAreCreator: Boolean,
    /**
     * When the creator ended the trip; null while it is live. The client hides the expense-editing
     * affordances of an ended trip rather than offering buttons the server will refuse with 409.
     */
    val closedAt: Instant?,
)

/** GroupsHome: every group, the per-currency overall figures, and how many are settled. */
data class TripsView(
    val trips: List<TripView>,
    /**
     * One total per currency the viewer holds a trip in. Summing across currencies would render a
     * meaningless figure — ¥ added to $ — so each currency stands on its own line.
     */
    val overalls: List<CurrencyTotalView>,
    val settledTripCount: Int,
)

data class CurrencyTotalView(val currencyCode: String, val netMinor: Long)

data class InviteView(val token: String, val expiresAt: Instant)

/**
 * What a share link is allowed to show before its holder is on the trip: the trip's name and the
 * names still free to claim. Deliberately not [TripView] — no items, no balances, no claimed
 * members, because the holder of a link is not yet somebody the trip's numbers belong to.
 */
data class ClaimableView(val tripName: String, val you: ClaimableMemberView?, val members: List<ClaimableMemberView>)

data class ClaimableMemberView(val id: UUID, val displayName: String, val personHue: Short)
