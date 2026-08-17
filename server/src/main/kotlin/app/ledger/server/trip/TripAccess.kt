package app.ledger.server.trip

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Every permission question about a trip, in one place.
 *
 * Extracted so items ask it the same way trips do. A second copy of "can this person see this
 * trip" is how the two copies come to disagree.
 */
@Component
class TripAccess(
    private val trips: TripRepository,
    private val members: TripMemberRepository,
) {
    /**
     * Any trip member. Non-members get 404, not 403: to somebody with no business here, a trip
     * they cannot see should be indistinguishable from one that does not exist.
     */
    fun visibleTrip(tripId: UUID, actor: UUID): TripEntity {
        val trip = trips.findById(tripId).orElseThrow { notFound() }
        members.findByTripIdAndUserId(tripId, actor) ?: throw notFound()
        return trip
    }

    /**
     * Trip creator only — the roster rules (spec §5). 403, because they are on the trip, and a
     * ResponseStatusException so the reason rides the RFC 7807 body: a bare 403 the client cannot
     * explain is the legacy error shape problemdetails exists to replace.
     */
    fun creatorOnly(tripId: UUID, actor: UUID): TripEntity {
        val trip = visibleTrip(tripId, actor)
        if (trip.createdByUserId != actor) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the person who created this trip can do that")
        }
        return trip
    }

    fun memberOf(tripId: UUID, actor: UUID): TripMemberEntity =
        members.findByTripIdAndUserId(tripId, actor) ?: throw notFound()

    /**
     * Item payer plus trip creator — the expense edit right (spec §5), shared by the expense
     * itself and its receipt. The payer fronted the money, so it is theirs to correct; the
     * creator can correct anything; everybody else views and claims. Takes ids rather than an
     * ItemEntity so the trip package does not reach into item/.
     */
    fun expenseEditorOnly(tripId: UUID, payerMemberId: UUID, actor: UUID): TripEntity {
        val trip = visibleTrip(tripId, actor)
        val payer = members.findById(payerMemberId).orElseThrow { notFound() }
        if (payer.userId != actor && trip.createdByUserId != actor) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only the person who paid, or the trip's creator, can change this expense",
            )
        }
        return trip
    }

    /**
     * Refuses changes to an ended trip's spending record. A 409, not 403: the caller may well
     * hold the right — the trip's state is what stands in the way, and reopening is the remedy.
     * Paybacks and settle-up are deliberately *not* behind this: people square up after a trip
     * ends, which is the whole reason receipts outlive it by 14 days.
     */
    fun requireOpen(trip: TripEntity) {
        if (trip.closedAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This trip has ended — reopen it to change its expenses")
        }
    }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "No such trip")
}
