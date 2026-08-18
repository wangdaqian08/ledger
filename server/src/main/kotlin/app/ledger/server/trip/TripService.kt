package app.ledger.server.trip

import app.ledger.server.invite.InvalidInviteToken
import app.ledger.server.invite.InviteTokens
import app.ledger.server.invite.IssuedInvite
import app.ledger.server.item.toView
import app.ledger.server.user.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
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
    private val clock: Clock,
    private val properties: TripProperties,
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

    /**
     * Any trip you hold a claimed member slot on, and nothing else — minus anything its creator
     * has deleted, plus, if you are that creator, the list of what you could still bring back.
     *
     * Hidden trips are in [TripsView.trips] like any other. The screen decides what to show; the
     * payload decides what is true, and a hidden trip's balance is still your balance.
     */
    @Transactional(readOnly = true)
    fun listFor(actor: UUID): TripsView {
        val deleted = trips.findAllDeletedForCreator(actor).map { it.toDeletedView(properties.restoreWindow) }
        val visible = trips.findAllForUser(actor)
        if (visible.isEmpty()) return TripsView(emptyList(), emptyList(), 0, deleted)

        val loaded = snapshots.loadAll(visible.map { it.id })
        val views = visible.mapNotNull { trip -> loaded[trip.id]?.let { trip.toView(it, actor) } }

        return TripsView(
            trips = views,
            // One line per currency, each summed on its own. Sorted so the home screen is stable
            // rather than at the mercy of trip order.
            overalls = views
                .groupBy { it.currencyCode }
                .map { (code, group) -> CurrencyTotalView(code, group.sumOf { it.yourNetMinor }) }
                .sortedBy { it.currencyCode },
            // "Settled" is being square with the group, which is what the GroupsHome badge means.
            settledTripCount = views.count { it.yourNetMinor == 0L },
            deleted = deleted,
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

        val member = TripMemberEntity(tripId = trip.id, displayName = displayName, personHue = nextHue(trip.id))
        val saved = try {
            // Flushed inside the transaction so two simultaneous adds of the same name become one
            // 409, the same answer the second would get arriving a moment later — not a bare 500.
            members.saveAndFlush(member)
        } catch (race: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Somebody on this trip already has that name", race)
        }
        return saved.toMemberView(actor)
    }

    /**
     * Trip creator only, like [addMember] — the roster is the creator's, even for a seat somebody
     * has claimed. A name is the roster's one hand-entered fact, so it is the one that gets typed
     * wrong, and fixing it moves no number: nothing financial hangs off a display name.
     */
    @Transactional
    fun renameMember(tripId: UUID, memberId: UUID, command: RenameMember, actor: UUID): MemberView {
        val trip = access.creatorOnly(tripId, actor)
        val member = members
            .findById(memberId)
            .filter { it.tripId == trip.id }
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No such member on this trip") }
        val displayName = command.displayName.trim()

        // Re-casing the same seat ("bobb" → "Bobb") only collides with itself; landing on another
        // member's name is the real conflict, checked exactly like [addMember].
        if (!member.displayName.equals(displayName, ignoreCase = true) &&
            members.existsByTripIdAndDisplayNameIgnoreCase(trip.id, displayName)
        ) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Somebody on this trip already has that name")
        }

        member.displayName = displayName
        try {
            members.flush()
        } catch (race: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Somebody on this trip already has that name", race)
        }
        return member.toMemberView(actor)
    }

    /** Trip creator only: the share link is how the roster gets filled, so it follows the roster rule. */
    @Transactional(readOnly = true)
    fun invite(tripId: UUID, actor: UUID): IssuedInvite = inviteTokens.issue(access.creatorOnly(tripId, actor).id)

    /**
     * Trip creator only. Ending a trip closes its spending record — no new or changed expenses —
     * while paybacks and settle-up stay open, because squaring up happens after everyone flies
     * home. It also starts the 14-day clock after which receipt images are deleted.
     */
    @Transactional
    fun close(tripId: UUID, actor: UUID): TripView {
        val trip = access.creatorOnly(tripId, actor)
        if (trip.closedAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This trip has already ended")
        }
        trip.closedAt = clock.instant()
        return view(trip, actor)
    }

    /**
     * Trip creator only, and the undo for [close] — nobody gets trapped in a wrong record. It does
     * not resurrect receipts the retention sweep has already deleted.
     */
    @Transactional
    fun reopen(tripId: UUID, actor: UUID): TripView {
        val trip = access.creatorOnly(tripId, actor)
        if (trip.closedAt == null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This trip has not ended")
        }
        trip.closedAt = null
        return view(trip, actor)
    }

    /**
     * Trip creator only, and only once the trip has ended. Hiding takes it off every member's home
     * list and changes nothing else whatsoever: the link still opens it, settling and approvals
     * carry on as they do for any ended trip, and its balance still counts in everyone's overall
     * position. Tidying, not secrecy — which is why the screen lets anyone reveal what is hidden.
     */
    @Transactional
    fun hide(tripId: UUID, actor: UUID): TripView {
        val trip = access.creatorOnly(tripId, actor)
        if (trip.closedAt == null) {
            // A live trip vanishing from twelve home screens while people are still adding
            // expenses to it has no good reason to happen; the CHECK constraint says so too.
            // 409 rather than 403: the caller holds the right, the trip's state is what stands
            // in the way, and ending it is the remedy.
            throw ResponseStatusException(HttpStatus.CONFLICT, "End this trip before putting it away")
        }
        // Idempotent, unlike close: no clock hangs off this timestamp, so hiding twice is a no-op
        // rather than a 409 — while a second close would restart the receipt retention window.
        if (trip.hiddenAt == null) trip.hiddenAt = clock.instant()
        return view(trip, actor)
    }

    /** Trip creator only, and the undo for [hide]. Also idempotent, for the same reason. */
    @Transactional
    fun unhide(tripId: UUID, actor: UUID): TripView {
        val trip = access.creatorOnly(tripId, actor)
        trip.hiddenAt = null
        return view(trip, actor)
    }

    /**
     * Trip creator only, at any point in a trip's life. It leaves every member's list at once and
     * 404s from here by link, by invite and by API — but every row stays exactly where it is, so
     * [restore] can bring the whole trip back for the next [TripProperties.restoreWindow], after
     * which [TripPurge] destroys it.
     *
     * Nothing here checks whether money is outstanding. That is deliberate: refusing would trap a
     * trip somebody abandoned mid-settle forever, and warning is the client's job — the delete
     * dialog names what is still owed *before* this is called, which is where a warning can still
     * change the outcome.
     */
    @Transactional
    fun delete(tripId: UUID, actor: UUID) {
        access.creatorOnly(tripId, actor).deletedAt = clock.instant()
    }

    /**
     * The undo for [delete], creator only, from the Recently deleted section. Anything the sweep
     * has not taken yet can still come back — a trip that is present is a trip that is restorable,
     * which keeps the rule the user can see (the row is on screen) rather than one they cannot
     * (a clock they would have to have been counting).
     */
    @Transactional
    fun restore(tripId: UUID, actor: UUID): TripView {
        val trip = trips.findById(tripId).orElseThrow { noSuchTrip() }
        if (trip.deletedAt == null) {
            // Not deleted, so answer about it the way the rest of the API would: a stranger still
            // learns nothing (404), a member who is not the creator gets 403, and the creator is
            // told the plain truth that there is nothing to undo.
            access.creatorOnly(tripId, actor)
            throw ResponseStatusException(HttpStatus.CONFLICT, "This trip has not been deleted")
        }
        // Deleted means gone for everyone but the person who deleted it; anybody else gets the
        // same 404 they would get for a trip that never existed.
        if (trip.createdByUserId != actor) throw noSuchTrip()
        trip.deletedAt = null
        return view(trip, actor)
    }

    /**
     * The share link's landing page: which names are still free. Like [claim], deliberately not
     * behind [TripAccess.visibleTrip] — the token is the authorisation — and deliberately *only*
     * the unclaimed names: the link's holder is not yet somebody the trip's numbers belong to.
     *
     * The one exception is [ClaimableView.you]: the caller's own slot, when they already hold one.
     * Telling somebody what they already are leaks nothing about anyone else, and it lets the join
     * screen say "you are already aboard" instead of offering names every one of which [claim]
     * could only refuse.
     */
    @Transactional(readOnly = true)
    fun claimable(tripId: UUID, token: String, actor: UUID): ClaimableView {
        if (inviteTokens.verify(token) != tripId) {
            throw InvalidInviteToken("This invite link is for a different trip")
        }
        val trip = liveTrip(tripId)
        return ClaimableView(
            tripName = trip.name,
            you = members.findByTripIdAndUserId(tripId, actor)?.let {
                ClaimableMemberView(it.id, it.displayName, it.personHue)
            },
            members = members
                .findAllByTripIdOrderByCreatedAt(tripId)
                .filter { it.userId == null }
                .map { ClaimableMemberView(it.id, it.displayName, it.personHue) },
        )
    }

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

        val trip = liveTrip(tripId)
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
        try {
            members.flush()
        } catch (raceLost: DataIntegrityViolationException) {
            // Two claims can pass the check above together — the same link open on two devices.
            // trip_members_user_trip_unique lets only one of them keep a slot; the other lands
            // here and gets the same answer it would have got arriving a moment later.
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "You are already on this trip under another name",
                raceLost,
            )
        }
        return view(trip, actor)
    }

    /** Round-robin over the eight person hues, and never reassigned once given. */
    private fun nextHue(tripId: UUID): Short = ((members.countByTripId(tripId) % 8) + 1).toShort()

    /**
     * A trip that still exists, for the two paths a share link authorises rather than membership.
     * A deleted trip answers 404 here exactly as it does everywhere else — a link handed out
     * before the delete must not be a way back into it.
     */
    private fun liveTrip(tripId: UUID): TripEntity {
        val trip = trips.findById(tripId).orElseThrow { noSuchTrip() }
        if (trip.deletedAt != null) throw noSuchTrip()
        return trip
    }

    private fun noSuchTrip() = ResponseStatusException(HttpStatus.NOT_FOUND, "No such trip")

    private fun view(trip: TripEntity, actor: UUID): TripView {
        members.flush()
        return trip.toView(snapshots.load(trip.id), actor)
    }
}

private fun TripEntity.toView(snapshot: TripSnapshot, actor: UUID): TripView {
    val you = snapshot.memberFor(actor)
    return TripView(
        id = id,
        name = name,
        icon = icon,
        hue = hue,
        currencyCode = currencyCode,
        startsOn = startsOn,
        endsOn = endsOn,
        members = snapshot.roster.map { it.toMemberView(actor) },
        items = snapshot.items.map { snapshot.toView(it, actor) },
        yourNetMinor = you?.let { snapshot.netFor(it.id) } ?: 0L,
        // The three headline figures, derived here so TripScreen shows them rather than re-summing
        // the items itself — a locally recomputed total is exactly the stale number the design avoids.
        groupSpendMinor = snapshot.items.sumOf { it.amountMinor },
        yourShareMinor = you?.let { me -> snapshot.items.sumOf { snapshot.sharesOf(it)[me.id] ?: 0L } } ?: 0L,
        youFrontedMinor =
            you?.let { me -> snapshot.items.filter { it.payerMemberId == me.id }.sumOf { it.amountMinor } } ?: 0L,
        youAreCreator = createdByUserId == actor,
        closedAt = closedAt,
        hiddenAt = hiddenAt,
    )
}

private fun TripMemberEntity.toMemberView(actor: UUID) = MemberView(
    id = id,
    displayName = displayName,
    personHue = personHue,
    claimed = userId != null,
    isYou = userId == actor,
)

private fun TripEntity.toDeletedView(purgesAfter: Duration) = DeletedTripView(
    id = id,
    name = name,
    icon = icon,
    hue = hue,
    deletedAt = deletedAt,
    // The date the row disappears for good, computed here rather than left to the client: the
    // window is a server setting, and a client counting its own thirty days would promise a
    // deadline the sweep does not actually keep.
    purgesAt = deletedAt?.plus(purgesAfter),
)
