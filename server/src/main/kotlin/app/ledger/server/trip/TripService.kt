package app.ledger.server.trip

import app.ledger.server.invite.InvalidInviteToken
import app.ledger.server.invite.InviteTokens
import app.ledger.server.invite.IssuedInvite
import app.ledger.server.item.toView
import app.ledger.server.user.UserRepository
import org.springframework.http.HttpStatus
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
    private val snapshots: TripSnapshots,
    private val access: TripAccess,
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

        val loaded = snapshots.loadAll(visible.map { it.id })
        val views = visible.mapNotNull { trip -> loaded[trip.id]?.let { trip.toView(it, actor) } }

        return TripsView(
            trips = views,
            overallNetMinor = views.sumOf { it.yourNetMinor },
            // "Settled" is being square with the group, which is what the GroupsHome badge means.
            settledTripCount = views.count { it.yourNetMinor == 0L },
        )
    }

    @Transactional(readOnly = true)
    fun detail(tripId: UUID, actor: UUID): TripView = view(access.visibleTrip(tripId, actor), actor)

    /** Trip creator only (spec §5, permissions). */
    @Transactional
    fun addMember(tripId: UUID, command: AddMember, actor: UUID): MemberView {
        val trip = access.creatorOnly(tripId, actor)
        val displayName = command.displayName.trim()

        // The database enforces this too, but exactly and case-sensitively. Two members called
        // "bob" and "Bob" on one trip is a person picking the wrong name at claim time.
        if (members.existsByTripIdAndDisplayNameIgnoreCase(trip.id, displayName)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Somebody on this trip already has that name")
        }

        val member = members.save(
            TripMemberEntity(tripId = trip.id, displayName = displayName, personHue = nextHue(trip.id)),
        )
        return member.toMemberView(actor)
    }

    /** Trip creator only: the share link is how the roster gets filled, so it follows the roster rule. */
    @Transactional(readOnly = true)
    fun invite(tripId: UUID, actor: UUID): IssuedInvite = inviteTokens.issue(access.creatorOnly(tripId, actor).id)

    /**
     * Deliberately not behind [TripAccess.visibleTrip]: the whole point is that the caller is *not*
     * yet a member. The token is the authorisation, which is why it is verified before anything
     * else and why the trip it names must match the one in the path.
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
        members.flush()
        return view(trip, actor)
    }

    /** Round-robin over the eight person hues, and never reassigned once given. */
    private fun nextHue(tripId: UUID): Short = ((members.countByTripId(tripId) % 8) + 1).toShort()

    private fun view(trip: TripEntity, actor: UUID): TripView {
        members.flush()
        return trip.toView(snapshots.load(trip.id), actor)
    }
}

private fun TripEntity.toView(snapshot: TripSnapshot, actor: UUID) = TripView(
    id = id,
    name = name,
    icon = icon,
    hue = hue,
    currencyCode = currencyCode,
    startsOn = startsOn,
    endsOn = endsOn,
    members = snapshot.roster.map { it.toMemberView(actor) },
    items = snapshot.items.map { snapshot.toView(it, actor) },
    yourNetMinor = snapshot.memberFor(actor)?.let { snapshot.netFor(it.id) } ?: 0L,
)

private fun TripMemberEntity.toMemberView(actor: UUID) = MemberView(
    id = id,
    displayName = displayName,
    personHue = personHue,
    claimed = userId != null,
    isYou = userId == actor,
)
