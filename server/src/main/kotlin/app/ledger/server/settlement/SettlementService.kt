package app.ledger.server.settlement

import app.ledger.engine.MemberId
import app.ledger.server.payback.PaybackEntity
import app.ledger.server.payback.PaybackRepository
import app.ledger.server.payback.PaybackStatusName
import app.ledger.server.payback.PaybackView
import app.ledger.server.payback.toView
import app.ledger.server.trip.TripAccess
import app.ledger.server.trip.TripSnapshots
import java.time.LocalDate
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class SettlementService(
    private val paybacks: PaybackRepository,
    private val snapshots: TripSnapshots,
    private val access: TripAccess,
) {
    /** Your position with every other person on the trip, one row each. */
    @Transactional(readOnly = true)
    fun forViewer(tripId: UUID, actor: UUID): SettlementView {
        val trip = access.visibleTrip(tripId, actor)
        val snapshot = snapshots.load(tripId)
        val you = snapshot.memberFor(actor)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such trip")

        val userIdOf = snapshot.roster.associate { it.id to it.userId }

        fun view(payback: PaybackEntity) = payback.toView(actor, trip.createdByUserId) { userIdOf[it] }

        fun theOther(payback: PaybackEntity) =
            if (payback.fromMemberId == you.id) payback.toMemberId else payback.fromMemberId

        // Trip-level settlements between you and someone, grouped by that someone. Pending ones still
        // count as unpaid and can be decided; approved ones have already moved the balance but keep a
        // visible, undoable record, so a mistaken confirmation is not a one-way door (§7a).
        val mine = snapshot.paybacks.filter {
            it.itemId == null && (it.fromMemberId == you.id || it.toMemberId == you.id)
        }
        val pendingByOther = mine.filter { it.status == PaybackStatusName.PENDING }.groupBy(::theOther)
        val settledByOther = mine.filter { it.status == PaybackStatusName.APPROVED }.groupBy(::theOther)
        // Ones you filed and they turned down, handed back to you (the claimant) with the reason —
        // a settlement has no bill sheet to carry a rejection, so this row is where it must land.
        // Only your own: a decline you made needs no echo to yourself.
        val rejectedByOther = mine
            .filter { it.status == PaybackStatusName.REJECTED && it.fromMemberId == you.id }
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

        return SettlementView(
            rows = rows,
            yourNetMinor = snapshot.netFor(you.id),
            allSquare = rows.all { it.owedMinor == 0L },
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
        val trip = access.visibleTrip(tripId, actor)
        val snapshot = snapshots.load(tripId)
        val you = snapshot.memberFor(actor)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such trip")
        val them = snapshot.roster.firstOrNull { it.id == command.toMemberId }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "That person is not on this trip")

        if (them.id == you.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot settle up with yourself")
        }

        // Deliberately not capped at what you currently owe. Paying more than the running figure is
        // a real thing people do — rounding a debt up, or covering something not yet entered — and
        // refusing it would be the app telling somebody they are wrong about their own money.
        val userIdOf = snapshot.roster.associate { it.id to it.userId }
        return paybacks
            .save(
                PaybackEntity(
                    tripId = trip.id,
                    itemId = null,
                    fromMemberId = you.id,
                    toMemberId = them.id,
                    amountMinor = command.amountMinor,
                    paidOn = LocalDate.now(),
                    status = PaybackStatusName.PENDING,
                    createdByUserId = actor,
                ),
            ).toView(actor, trip.createdByUserId) { userIdOf[it] }
    }

    /**
     * A nudge to somebody who owes you. Changes no balance and writes no payback (§7a).
     *
     * **It does not yet deliver anything.** Push notifications are explicitly out of phase 1 (§9),
     * so this validates that the nudge makes sense — they are on the trip, and they really do owe
     * you — and then returns. The endpoint exists so the button can be wired and the rule lives
     * somewhere; sending is the part still missing, and no caller should be told otherwise.
     */
    @Transactional(readOnly = true)
    fun remind(tripId: UUID, command: Remind, actor: UUID) {
        access.visibleTrip(tripId, actor)
        val snapshot = snapshots.load(tripId)
        val you = snapshot.memberFor(actor)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such trip")
        val them = snapshot.roster.firstOrNull { it.id == command.memberId }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "That person is not on this trip")

        if (them.id == you.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot remind yourself")
        }
        if (snapshot.owesBetween(them.id, you.id) <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "They do not owe you anything")
        }
    }

    /**
     * Every Family in the completed partition (§7b): the viewer's explicit Families, plus everyone
     * else as an automatic one-person Family. Pre-validates every condition explicitly, the same
     * division of labour as [app.ledger.server.item.ItemService]'s own roster validation, so the
     * engine's own `require()`s are unreachable in practice.
     */
    @Transactional(readOnly = true)
    fun families(tripId: UUID, command: PreviewFamilies, actor: UUID): FamiliesView {
        access.visibleTrip(tripId, actor)
        val snapshot = snapshots.load(tripId)
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

        fun membersOf(family: app.ledger.engine.Family): List<FamilyMemberView> =
            snapshot.roster
                .filter { MemberId(it.id.toString()) in family.members }
                .map { FamilyMemberView(it.id, it.displayName, it.personHue) }

        return FamiliesView(
            snapshot.families(explicit).map { balance ->
                FamilyView(
                    members = membersOf(balance.family),
                    netMinor = balance.netMinor,
                    counterparts = balance.betweenFamilies.map { (other, owed) ->
                        FamilyCounterpartView(membersOf(other), owed)
                    },
                )
            },
        )
    }
}
