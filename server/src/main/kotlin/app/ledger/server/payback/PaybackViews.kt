package app.ledger.server.payback

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PaybackView(
    val id: UUID,
    val itemId: UUID?,
    val fromMemberId: UUID,
    val toMemberId: UUID,
    val amountMinor: Long,
    val paidOn: LocalDate,
    val note: String?,
    val status: PaybackStatusName,
    val proofObjectName: String?,
    val rejectReason: String?,
    val reviewedAt: Instant?,
    /**
     * The two authorization questions the client must never answer for itself. [viewerCanDecide] is
     * whether this viewer may approve or reject the claim now; [viewerCanUndo] whether they may undo
     * it. Both are computed from the same predicates the endpoints enforce ([reviewableBy],
     * [undoableBy]), so a control only ever appears where the server will accept it — the drift
     * between "what the button offers" and "what the server allows" cannot happen.
     */
    val viewerCanDecide: Boolean,
    val viewerCanUndo: Boolean,
)

/**
 * May [actor] approve or reject this claim — the reviewable rule (spec §3, §7a): the person owed, or
 * the trip's creator, but never the person paying, save a creator vouching for a recipient who has
 * never signed in and so cannot confirm it themselves. Authorization only; callers layer the
 * PENDING-status check on top.
 *
 * The same predicate backs both the 403 the endpoint throws and the flag the view carries, so there
 * is one rule, in one place.
 */
fun PaybackEntity.reviewableBy(actor: UUID, creatorUserId: UUID, userIdOf: (UUID) -> UUID?): Boolean {
    val recipientUserId = userIdOf(toMemberId)
    val payerUserId = userIdOf(fromMemberId)
    if (recipientUserId != actor && creatorUserId != actor) return false
    if (payerUserId == actor && recipientUserId != null) return false
    return true
}

/** May [actor] undo this claim — either party, or the trip's creator (spec §7a). */
fun PaybackEntity.undoableBy(actor: UUID, creatorUserId: UUID, userIdOf: (UUID) -> UUID?): Boolean =
    userIdOf(fromMemberId) == actor || userIdOf(toMemberId) == actor || creatorUserId == actor

fun PaybackEntity.toView(
    actor: UUID,
    creatorUserId: UUID,
    userIdOf: (UUID) -> UUID?,
): PaybackView = PaybackView(
    id = id,
    itemId = itemId,
    fromMemberId = fromMemberId,
    toMemberId = toMemberId,
    amountMinor = amountMinor,
    paidOn = paidOn,
    note = note,
    status = status,
    proofObjectName = proofObjectName,
    rejectReason = rejectReason,
    reviewedAt = reviewedAt,
    viewerCanDecide = status == PaybackStatusName.PENDING && reviewableBy(actor, creatorUserId, userIdOf),
    viewerCanUndo = undoableBy(actor, creatorUserId, userIdOf),
)
