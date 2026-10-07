package app.ledger.engine

/**
 * Divides [totalMinor] equally between [members], in minor currency units.
 *
 * The parts always sum to exactly [totalMinor] — never a cent more or less.
 */
fun splitEqually(totalMinor: Long, members: List<MemberId>, salt: Long = 0): Map<MemberId, Long> =
    shares(totalMinor, members, SplitRule.Equal, salt)

/** A repayment towards this item. The recipient can only ever be the person who fronted it. */
fun Item.repaidBy(
    from: MemberId,
    amountMinor: Long,
    status: PaybackStatus = PaybackStatus.APPROVED,
): Payback = Payback(from = from, to = payer, amountMinor = amountMinor, status = status, itemId = id)
