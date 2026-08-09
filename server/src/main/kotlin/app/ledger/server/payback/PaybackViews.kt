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
)

fun PaybackEntity.toView(): PaybackView = PaybackView(
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
)
