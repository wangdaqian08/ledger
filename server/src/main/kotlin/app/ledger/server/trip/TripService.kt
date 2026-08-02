package app.ledger.server.trip

import app.ledger.engine.MemberId
import app.ledger.engine.Trip
import app.ledger.engine.settle
import app.ledger.server.invite.InvalidInviteToken
import app.ledger.server.invite.InviteTokens
import app.ledger.server.invite.IssuedInvite
import app.ledger.server.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.Currency
import java.util.UUID

@Service
class TripService(
    private val trips: TripRepository,
    private val members: TripMemberRepository,
    private val users: UserRepository,
    private val inviteTokens: InviteTokens,
) {
    /** Anyone signed in. Creating a trip makes you its first member, already claimed. */
    @Transactional
    fun create(command: CreateTrip, actor: UUID): TripView {
        val currency = runCatching { Currency.getInstance(command.currencyCode.uppercase()) }
            .getOrElse { throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown currency code") }
        if (command.endsOn != null && command.startsOn != null && command.endsOn < command.startsOn) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A trip cannot end before it starts")
        }

        val creator = users.findById(actor).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No such user")
        }
        val trip = trips.save(
            TripEntity(
                name = command.name.trim(),
                icon = command.icon.trim(),
                hue = command.hue.toShort(),
                currencyCode = currency.currencyCode,
                createdByUserId = actor,
                startsOn = command.startsOn,
                endsOn = command.endsOn,
            ),
        )

        // Without this the creator would not appear in their own trip's member list, and
        // findAllForUser — which keys off claimed membership — would not return it to them.
        members.save(
            TripMemberEntity(
                tripId = trip.id,
                displayName = creator.displayName,
                personHue = nextHue(trip.id),
                userId = actor,
            ),
        )

        return view(trip, actor)
    }

    /** Any trip you hold a claimed member slot on, and nothing else. */
    @Transactional(readOnly = true)
    fun listFor(actor: UUID): TripsView {
        val visible = trips.findAllForUser(actor)
        if (visible.isEmpty()) return TripsView(emptyList(), 0, 0)

        val byTrip = members.findAllByTripIdInOrderByCreatedAt(visible.map { it.id }).groupBy { it.tripId }
        val views = visible.map { trip -> view(trip, actor, byTrip[trip.id].orEmpty()) }

        return TripsView(
            trips = views,
            overallNetMinor = views.sumOf { it.yourNetMinor },
            settledTripCount = views.count { it.yourNetMinor == 0L },
        )
    }

    /** Any trip member. Non-members get 404 rather than 403 — see [visibleTrip]. */
    @Transactional(readOnly = true)
    fun detail(tripId: UUID, actor: UUID): TripView = view(visibleTrip(tripId, actor), actor)

    /** Trip creator only (spec §5, permissions). */
    @Transactional
    fun addMember(tripId: UUID, command: AddMember, actor: UUID): MemberView {
        val trip = creatorOnly(tripId, actor)
        val displayName = command.displayName.trim()

        // The database enforces this too, but exactly and case-sensitively. Two members called
        // "bob" and "Bob" on one trip is a person picking the wrong name at claim time.
        if (members.existsByTripIdAndDisplayNameIgnoreCase(trip.id, displayName)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Somebody on this trip already has that name")
        }

        val member = members.save(
            TripMemberEntity(tripId = trip.id, displayName = displayName, personHue = nextHue(trip.id)),
        )
        return member.toView(actor)
    }

    /** Trip creator only: the share link is how the roster gets filled, so it follows the roster rule. */
    @Transactional(readOnly = true)
    fun invite(tripId: UUID, actor: UUID): IssuedInvite = inviteTokens.issue(creatorOnly(tripId, actor).id)

    /**
     * Deliberately not behind [visibleTrip]: the whole point is that the caller is *not* yet a
     * member. The token is the authorisation, which is why it is verified before anything else and
     * why the trip it names must match the one in the path.
     */
    @Transactional
    fun claim(tripId: UUID, command: ClaimMember, actor: UUID): TripView {
        if (inviteTokens.verify(command.token) != tripId) {
            throw InvalidInviteToken("This invite link is for a different trip")
        }

        val trip = trips.findById(tripId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No such trip")
        }
        val member = members
            .findById(command.memberId)
            .filter { it.tripId == tripId }
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No such member on this trip") }

        if (member.userId != null) {
            val message =
                if (member.userId == actor) {
                    "You have already claimed this name"
                } else {
                    // Not "that name is taken by Sam" — the holder of a share link is not
                    // necessarily on the trip, and need not be told who is.
                    "Somebody has already claimed that name"
                }
            throw ResponseStatusException(HttpStatus.CONFLICT, message)
        }
        if (members.findByTripIdAndUserId(tripId, actor) != null) {
            // One person is one member. Holding two slots would let them owe and be owed as two
            // different people on the same trip, and the balances would still sum to zero while
            // being nonsense.
            throw ResponseStatusException(HttpStatus.CONFLICT, "You are already on this trip under another name")
        }

        member.userId = actor
        return view(trip, actor)
    }

    private fun visibleTrip(tripId: UUID, actor: UUID): TripEntity {
        val trip = trips.findById(tripId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No such trip")
        }
        // 404 rather than 403 on purpose: to somebody with no business here, a trip they cannot see
        // should be indistinguishable from one that does not exist.
        members.findByTripIdAndUserId(tripId, actor)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such trip")
        return trip
    }

    private fun creatorOnly(tripId: UUID, actor: UUID): TripEntity {
        val trip = visibleTrip(tripId, actor)
        if (trip.createdByUserId != actor) {
            throw AccessDeniedException("Only the person who created this trip can do that")
        }
        return trip
    }

    /** Round-robin over the eight person hues, and never reassigned once given. */
    private fun nextHue(tripId: UUID): Short = ((members.countByTripId(tripId) % 8) + 1).toShort()

    private fun view(
        trip: TripEntity,
        actor: UUID,
        roster: List<TripMemberEntity> = members.findAllByTripIdOrderByCreatedAt(trip.id),
    ): TripView = TripView(
        id = trip.id,
        name = trip.name,
        icon = trip.icon,
        hue = trip.hue,
        currencyCode = trip.currencyCode,
        startsOn = trip.startsOn,
        endsOn = trip.endsOn,
        members = roster.map { it.toView(actor) },
        yourNetMinor = netFor(roster, actor),
    )

    /**
     * Asks the engine rather than returning a hardcoded zero. There are no items until build order
     * step 5, so every answer here is currently 0 — but the mapping from rows to
     * [app.ledger.engine.Trip] is the seam step 5 hangs off, and a seam nothing exercises is a seam
     * nobody knows is broken.
     */
    private fun netFor(roster: List<TripMemberEntity>, actor: UUID): Long {
        val you = roster.firstOrNull { it.userId == actor } ?: return 0
        val trip = Trip(members = roster.map { MemberId(it.id.toString()) }, items = emptyList())
        return settle(trip).net(MemberId(you.id.toString()))
    }

    private fun TripMemberEntity.toView(actor: UUID) = MemberView(
        id = id,
        displayName = displayName,
        personHue = personHue,
        claimed = userId != null,
        isYou = userId == actor,
    )
}
