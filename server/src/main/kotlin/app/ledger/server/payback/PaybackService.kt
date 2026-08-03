package app.ledger.server.payback

import app.ledger.server.item.ItemRepository
import app.ledger.server.item.ItemShareRepository
import app.ledger.server.trip.TripAccess
import app.ledger.server.trip.TripEntity
import app.ledger.server.trip.TripMemberRepository
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class PaybackService(
    private val paybacks: PaybackRepository,
    private val items: ItemRepository,
    private val shares: ItemShareRepository,
    private val members: TripMemberRepository,
    private val access: TripAccess,
) {
    /**
     * Claim that you have paid somebody back for a bill they fronted.
     *
     * The recipient is never supplied: on an item it can only be the payer, because they are the
     * one out of pocket. Submitted by the person paying, or by the person owed ticking somebody
     * off — and in that second case it is approved on the spot, since the only person whose
     * agreement is needed is the one recording it (spec §3).
     */
    @Transactional
    fun submitForItem(itemId: UUID, command: SubmitPayback, actor: UUID): PaybackView {
        val item = items.findById(itemId).orElseThrow { noSuchItem() }
        val trip = access.visibleTrip(item.tripId, actor)
        val payer = member(item.payerMemberId)

        if (command.fromMemberId == item.payerMemberId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The payer cannot pay themselves back")
        }
        val onTheBill = shares.findAllByIdItemId(item.id).map { it.id.memberId }.toSet()
        if (command.fromMemberId !in onTheBill) {
            // Money moving between two people who do not share this bill is a trip-level
            // settlement, not a repayment towards it — a different record with a different meaning.
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "That person is not sharing this expense")
        }

        val claimant = member(command.fromMemberId)
        val recordedByThePersonOwed = payer.userId == actor
        if (claimant.userId != actor && !recordedByThePersonOwed) {
            throw AccessDeniedException("Only you, or the person you owe, can record that you have paid")
        }

        return paybacks
            .save(
                PaybackEntity(
                    tripId = trip.id,
                    itemId = item.id,
                    fromMemberId = command.fromMemberId,
                    toMemberId = item.payerMemberId,
                    amountMinor = command.amountMinor,
                    paidOn = command.paidOn,
                    note = command.note?.trim()?.ifEmpty { null },
                    status = if (recordedByThePersonOwed) PaybackStatusName.APPROVED else PaybackStatusName.PENDING,
                    createdByUserId = actor,
                    reviewedByUserId = actor.takeIf { recordedByThePersonOwed },
                    reviewedAt = Instant.now().takeIf { recordedByThePersonOwed },
                ),
            ).toView()
    }

    /** The person owed, or the trip's creator (spec §5). */
    @Transactional
    fun approve(paybackId: UUID, actor: UUID): PaybackView {
        val payback = reviewable(paybackId, actor)
        payback.status = PaybackStatusName.APPROVED
        payback.rejectReason = null
        payback.reviewedByUserId = actor
        payback.reviewedAt = Instant.now()
        return payback.toView()
    }

    /** The person owed, or the trip's creator. A reason is required — it is what gets corrected. */
    @Transactional
    fun reject(paybackId: UUID, command: RejectPayback, actor: UUID): PaybackView {
        val payback = reviewable(paybackId, actor)
        payback.status = PaybackStatusName.REJECTED
        payback.rejectReason = command.reason.trim()
        payback.reviewedByUserId = actor
        payback.reviewedAt = Instant.now()
        return payback.toView()
    }

    /**
     * Correct a turned-down claim and send it back. The amount and date are the things that get
     * disputed, so they are what can change; who paid whom cannot, because that would make this a
     * different claim wearing the same id.
     */
    @Transactional
    fun resubmit(paybackId: UUID, command: ResubmitPayback, actor: UUID): PaybackView {
        val payback = paybacks.findById(paybackId).orElseThrow { noSuchPayback() }
        access.visibleTrip(payback.tripId, actor)

        if (member(payback.fromMemberId).userId != actor) {
            throw AccessDeniedException("Only the person who made this claim can correct it")
        }
        if (payback.status == PaybackStatusName.APPROVED) {
            // Already agreed. Undo it if it is wrong — editing an approved record behind the
            // approver's back is exactly what the two-party rule exists to prevent.
            throw ResponseStatusException(HttpStatus.CONFLICT, "This has been approved; undo it instead")
        }

        command.amountMinor?.let { payback.amountMinor = it }
        command.paidOn?.let { payback.paidOn = it }
        command.note?.let { payback.note = it.trim().ifEmpty { null } }
        payback.status = PaybackStatusName.PENDING
        payback.rejectReason = null
        payback.reviewedByUserId = null
        payback.reviewedAt = null

        return payback.toView()
    }

    /**
     * Either party, any time, before or after approval — the row simply returns to unpaid (§7a).
     * A settled trip can therefore un-settle, which is the accepted cost of never trapping
     * somebody in a record they know to be wrong.
     */
    @Transactional
    fun undo(paybackId: UUID, actor: UUID) {
        val payback = paybacks.findById(paybackId).orElseThrow { noSuchPayback() }
        val trip = access.visibleTrip(payback.tripId, actor)

        val isEitherParty = member(payback.fromMemberId).userId == actor || member(payback.toMemberId).userId == actor
        if (!isEitherParty && trip.createdByUserId != actor) {
            throw AccessDeniedException("Only the two people involved, or the trip's creator, can undo this")
        }
        paybacks.delete(payback)
    }

    @Transactional(readOnly = true)
    fun forItem(itemId: UUID, actor: UUID): List<PaybackView> {
        val item = items.findById(itemId).orElseThrow { noSuchItem() }
        access.visibleTrip(item.tripId, actor)
        return paybacks.findAllByItemIdOrderByCreatedAt(item.id).map { it.toView() }
    }

    /**
     * The approval rule, in one place: the person owed, or the trip's creator.
     *
     * The creator is included on the strength of §5's permissions table. It is worth being clear
     * about what it means — the creator can mark a debt between two other people as settled — and
     * it is deliberate, so that a trip does not stall when somebody stops answering their phone.
     */
    private fun reviewable(paybackId: UUID, actor: UUID): PaybackEntity {
        val payback = paybacks.findById(paybackId).orElseThrow { noSuchPayback() }
        val trip: TripEntity = access.visibleTrip(payback.tripId, actor)

        if (member(payback.toMemberId).userId != actor && trip.createdByUserId != actor) {
            throw AccessDeniedException("Only the person owed, or the trip's creator, can decide this")
        }
        if (payback.status != PaybackStatusName.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This has already been decided")
        }
        return payback
    }

    private fun member(memberId: UUID) = members.findById(memberId).orElseThrow { noSuchPayback() }

    private companion object {
        fun noSuchItem() = ResponseStatusException(HttpStatus.NOT_FOUND, "No such expense")

        fun noSuchPayback() = ResponseStatusException(HttpStatus.NOT_FOUND, "No such payback")
    }
}
