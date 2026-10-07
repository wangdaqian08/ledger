package app.ledger.server.settlement

import app.ledger.engine.Family
import app.ledger.engine.MemberId
import app.ledger.engine.PaybackStatus
import app.ledger.engine.familyTransfers
import app.ledger.server.payback.PaybackEntity
import app.ledger.server.payback.PaybackRepository
import app.ledger.server.payback.PaybackView
import app.ledger.server.payback.toView
import app.ledger.server.trip.TripAccess
import app.ledger.server.trip.TripEntity
import app.ledger.server.trip.TripMemberEntity
import app.ledger.server.trip.TripSnapshot
import app.ledger.server.trip.TripSnapshots
import app.ledger.server.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.UUID

@Service
class SettlementService(
    private val paybacks: PaybackRepository,
    private val snapshots: TripSnapshots,
    private val access: TripAccess,
    private val users: UserRepository,
) {
    /** Your position with every other person on the trip, one row each. */
    @Transactional(readOnly = true)
    fun forViewer(tripId: UUID, actor: UUID): SettlementView {
        val (trip, snapshot, you) = seat(tripId, actor)

        fun view(payback: PaybackEntity) = payback.toView(actor, trip.createdByUserId) { snapshot.userIdOf[it] }

        fun theOther(payback: PaybackEntity) =
            if (payback.fromMemberId == you.id) payback.toMemberId else payback.fromMemberId

        // Trip-level settlements between you and someone, grouped by that someone. Pending ones still
        // count as unpaid and can be decided; approved ones have already moved the balance but keep a
        // visible, undoable record, so a mistaken confirmation is not a one-way door (§7a).
        val mine = snapshot.paybacks.filter {
            it.itemId == null && (it.fromMemberId == you.id || it.toMemberId == you.id)
        }
        val pendingByOther = mine.filter { it.status == PaybackStatus.PENDING }.groupBy(::theOther)
        val settledByOther = mine.filter { it.status == PaybackStatus.APPROVED }.groupBy(::theOther)
        // Ones you filed and they turned down, handed back to you (the claimant) with the reason —
        // a settlement has no bill sheet to carry a rejection, so this row is where it must land.
        // Only your own: a decline you made needs no echo to yourself.
        val rejectedByOther = mine
            .filter { it.status == PaybackStatus.REJECTED && it.fromMemberId == you.id }
            .groupBy(::theOther)

        val rows = snapshot.roster
            .filter { it.id != you.id }
            .map { other ->
                SettlementRow(
                    memberId = other.id,
                    displayName = other.displayName,
                    personHue = other.personHue,
                    owedMinor = snapshot.owesBetween(you.id, other.id),
                    pending = pendingByOther[other.id].orEmpty().map(::view),
                    settled = settledByOther[other.id].orEmpty().map(::view),
                    rejected = rejectedByOther[other.id].orEmpty().map(::view),
                )
            }

        val yourNetMinor = snapshot.netFor(you.id)
        return SettlementView(
            rows = rows,
            yourNetMinor = yourNetMinor,
            // Your net, not your rows. Paying by the fewest-transfers list routes money past the
            // person it was originally owed to — Eve pays Cat what she owed Ben — so rows can cancel
            // without ever reaching zero. Waiting for every row would never call that trip square.
            allSquare = yourNetMinor == 0L,
            transfers = snapshot.transfers(::TransferView),
            breakdown = breakdown(snapshot, you.id),
        )
    }

    /**
     * "How it adds up" (§3), one row per person in roster order. Every figure is the engine's,
     * totals included: the browser renders the table and never sums it, because a client-side total
     * that disagreed would defeat the reason the table exists — letting a person check the maths.
     */
    private fun breakdown(snapshot: TripSnapshot, you: UUID): BreakdownView {
        val breakdown = snapshot.breakdown()
        val figuresOf = breakdown.rows.associateBy { it.member }
        return BreakdownView(
            rows = snapshot.roster.map { member ->
                val figures = figuresOf.getValue(MemberId(member.id.toString()))
                BreakdownRowView(
                    memberId = member.id,
                    displayName = member.displayName,
                    personHue = member.personHue,
                    isYou = member.id == you,
                    paidMinor = figures.frontedMinor,
                    shareMinor = figures.shareMinor,
                    settledMinor = figures.settledMinor,
                    netMinor = figures.netMinor,
                    transfersByPerson = figures.bilateralCount,
                    transfersFewest = figures.planCount,
                )
            },
            totals = BreakdownTotalsView(
                paidMinor = breakdown.frontedMinor,
                shareMinor = breakdown.shareMinor,
                settledMinor = breakdown.settledMinor,
                netMinor = breakdown.netMinor,
                transfersByPerson = breakdown.bilateralPairs,
                transfersFewest = breakdown.planTransfers,
            ),
        )
    }

    /**
     * Tapping Pay files a trip-level settlement: a payback with no item, from you to them, sitting
     * PENDING until they agree (§7a). It is a request, not an act — until it is approved the row
     * still counts as unpaid, which is the whole reason display-only settlement was abandoned.
     *
     * A trip-level settlement clears a person's overall position rather than one bill, which is why
     * it carries an explicit recipient instead of inferring one from a payer.
     */
    @Transactional
    fun pay(tripId: UUID, command: SubmitSettlement, actor: UUID): PaybackView {
        val (trip, snapshot, you) = seat(tripId, actor)
        val them = counterpart(snapshot, you, command.toMemberId, "You cannot settle up with yourself")

        // Deliberately not capped at what you currently owe. Paying more than the running figure is
        // a real thing people do — rounding a debt up, or covering something not yet entered — and
        // refusing it would be the app telling somebody they are wrong about their own money.
        return paybacks
            .save(
                PaybackEntity(
                    tripId = trip.id,
                    itemId = null,
                    fromMemberId = you.id,
                    toMemberId = them.id,
                    amountMinor = command.amountMinor,
                    paidOn = LocalDate.now(),
                    status = PaybackStatus.PENDING,
                    createdByUserId = actor,
                ),
            ).toView(actor, trip.createdByUserId) { snapshot.userIdOf[it] }
    }

    /**
     * A nudge to somebody who owes you. Changes no balance and writes no payback (§7a).
     *
     * **It does not yet deliver anything.** Push notifications are explicitly out of phase 1 (§9),
     * so this validates that the nudge makes sense — they are on the trip, they really do owe you,
     * and you are not square overall — and then returns. The endpoint exists so the button can be
     * wired and the rule lives somewhere; sending is the part still missing, and no caller should be
     * told otherwise.
     *
     * Square overall wins over a row. With a net of zero, whatever one person's row says they owe
     * you is cancelled by what you owe somebody else — the chain where Alice owes Bob and Bob owes
     * Carol, or a trip paid by the fewest-transfers list. Chasing it would collect money that is
     * not, in the end, yours; and it is the same rule that hides the Remind button (`allSquare`).
     */
    @Transactional(readOnly = true)
    fun remind(tripId: UUID, command: Remind, actor: UUID) {
        val (_, snapshot, you) = seat(tripId, actor)
        val them = counterpart(snapshot, you, command.memberId, "You cannot remind yourself")
        if (snapshot.owesBetween(them.id, you.id) <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "They do not owe you anything")
        }
        if (snapshot.netFor(you.id) == 0L) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "You're square overall — there is nothing to remind them of",
            )
        }
    }

    /**
     * Every Family in the completed partition (§7b): the viewer's explicit Families, plus everyone
     * else as an automatic one-person Family. Pre-validates every condition explicitly, the same
     * division of labour as [app.ledger.server.item.ItemService]'s own roster validation, so the
     * engine's own `require()`s are unreachable in practice.
     *
     * Beside the cards, the fewest-transfers plan with each Family as one party (§7a). It is read off
     * the partition's own nets, which came from the snapshot's cached settlement, so the whole
     * request still pays for one `settle()`.
     */
    @Transactional(readOnly = true)
    fun families(tripId: UUID, command: PreviewFamilies, actor: UUID): FamiliesView {
        val (trip, snapshot, you) = seat(tripId, actor)
        val onTrip = snapshot.roster.associateBy { it.id }

        if (command.families.any { it.memberIds.isEmpty() }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "a family can't be empty")
        }

        command.families.forEach { family ->
            if (family.memberIds.toSet().size != family.memberIds.size) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Somebody is listed twice in the same family")
            }
        }
        val explicit = command.families.map { it.memberIds.toSet() }

        if (!onTrip.keys.containsAll(explicit.flatten())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Somebody in a family is not on this trip")
        }

        val seen = mutableSetOf<UUID>()
        explicit.forEach { family ->
            if ((family intersect seen).isNotEmpty()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Somebody is in two families at once")
            }
            seen += family
        }

        if (explicit.size + (onTrip.keys - seen).size < 2) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "That covers everyone — a trip needs at least two families",
            )
        }

        fun rosterOf(family: Family): List<TripMemberEntity> =
            snapshot.roster.filter { MemberId(it.id.toString()) in family.members }

        fun membersOf(family: Family): List<FamilyMemberView> =
            rosterOf(family).map { FamilyMemberView(it.id, it.displayName, it.personHue, isYou = it.id == you.id) }

        val partition = snapshot.families(explicit)
        val plan = familyTransfers(partition)

        // Who has a PayID, for every account behind the roster in one query — a lookup per line, or
        // per member, would be the N+1 the snapshot exists to avoid. Skipped when nobody pays anybody.
        val accounts = snapshot.roster.mapNotNull { it.userId }.toSet()
        val withPayId: Set<UUID> = if (plan.isEmpty() || accounts.isEmpty()) {
            emptySet()
        } else {
            users.findAllById(accounts).filter { !it.payId.isNullOrBlank() }.mapTo(mutableSetOf()) { it.id }
        }
        val pendingSettlements = snapshot.paybacks.filter {
            it.itemId == null && it.status == PaybackStatus.PENDING
        }

        return FamiliesView(
            families = partition.map { balance ->
                FamilyView(
                    members = membersOf(balance.family),
                    netMinor = balance.netMinor,
                    counterparts = balance.betweenFamilies.map { (other, owed) ->
                        FamilyCounterpartView(membersOf(other), owed)
                    },
                )
            },
            transfers = plan.map { line ->
                val payers = rosterOf(line.from).map { it.id }.toSet()
                val payees = rosterOf(line.to)
                val payeeIds = payees.map { it.id }.toSet()
                FamilyTransferView(
                    from = membersOf(line.from),
                    to = membersOf(line.to),
                    amountMinor = line.amountMinor,
                    // Roster order on both counts: the first with a PayID, else simply the first.
                    payToMemberId = (payees.firstOrNull { it.userId in withPayId } ?: payees.first()).id,
                    pending = pendingSettlements
                        .filter { it.fromMemberId in payers && it.toMemberId in payeeIds }
                        .map { it.toView(actor, trip.createdByUserId) { member -> snapshot.userIdOf[member] } },
                )
            },
        )
    }

    /** The trip, loaded, and your seat on it — a 404 when it is not yours to see. */
    private fun seat(tripId: UUID, actor: UUID): Seat {
        val trip = access.visibleTrip(tripId, actor)
        val snapshot = snapshots.load(tripId)
        val you = snapshot.memberFor(actor)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such trip")
        return Seat(trip, snapshot, you)
    }

    /** The other person on a settle-up action: on this trip, and not you. */
    private fun counterpart(
        snapshot: TripSnapshot,
        you: TripMemberEntity,
        memberId: UUID,
        yourselfMessage: String,
    ): TripMemberEntity {
        val them = snapshot.roster.firstOrNull { it.id == memberId }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "That person is not on this trip")
        if (them.id == you.id) throw ResponseStatusException(HttpStatus.BAD_REQUEST, yourselfMessage)
        return them
    }

    private data class Seat(val trip: TripEntity, val snapshot: TripSnapshot, val you: TripMemberEntity)
}
