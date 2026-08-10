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

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "No such trip")
}
