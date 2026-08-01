package app.ledger.engine

/** Identifies one item (a cost) within a trip. */
@JvmInline
value class ItemId(val value: Long)

enum class PaybackStatus { PENDING, APPROVED, REJECTED }

/**
 * Money handed back to an item's payer by one of its sharers.
 *
 * A claim only counts once the payer has approved it — see [PaybackStatus].
 */
data class Payback(
    val from: MemberId,
    val amountMinor: Long,
    val status: PaybackStatus,
)

/**
 * Money that left the group, paid by exactly one person.
 *
 * [sharedBy] is the list of people the cost is divided between, and it stays editable for the
 * life of the trip — correcting it is the whole mechanism by which a changed headcount flows
 * through to everyone's balance. [split] decides how that division is done.
 */
data class Item(
    val id: ItemId,
    val amountMinor: Long,
    val payer: MemberId,
    val sharedBy: List<MemberId>,
    val paybacks: List<Payback> = emptyList(),
    /** Evenly by default; the demo's SplitBar drag produces [SplitRule.Weighted]. */
    val split: SplitRule = SplitRule.Equal,
)

data class Trip(
    val members: List<MemberId>,
    val items: List<Item>,
)

/** One person's position across the whole trip. */
data class MemberBalance(
    val member: MemberId,
    val paidOutMinor: Long,
    val receivedBackMinor: Long,
    val owedMinor: Long,
) {
    /** Positive means this person is owed money; negative means they owe it. */
    val netMinor: Long get() = paidOutMinor - receivedBackMinor - owedMinor
}

data class Transfer(val from: MemberId, val to: MemberId, val amountMinor: Long)

data class Settlement(
    val balances: List<MemberBalance>,
    val transfers: List<Transfer>,
) {
    fun net(member: MemberId): Long =
        balances.first { it.member == member }.netMinor
}

fun settle(trip: Trip): Settlement {
    val balances = trip.members.map { member ->
        val itemsTheyPaidFor = trip.items.filter { it.payer == member }

        MemberBalance(
            member = member,
            paidOutMinor = itemsTheyPaidFor.sumOf { it.amountMinor } +
                trip.items.sumOf { item ->
                    item.approvedPaybacks().filter { it.from == member }.sumOf { it.amountMinor }
                },
            receivedBackMinor = itemsTheyPaidFor.sumOf { item ->
                item.approvedPaybacks().sumOf { it.amountMinor }
            },
            owedMinor = trip.items.sumOf { it.shareOf(member) },
        )
    }
    return Settlement(balances, suggestTransfers(balances))
}

/**
 * The shortest practical list of payments that clears everyone.
 *
 * Greedy: repeatedly match the largest creditor against the largest debtor. Each step zeroes
 * out at least one person, so this never needs more than one transfer fewer than there are
 * people — typically three or four rather than one per head.
 */
private fun suggestTransfers(balances: List<MemberBalance>): List<Transfer> {
    val owed = balances.filter { it.netMinor > 0 }
        .sortedByDescending { it.netMinor }
        .map { it.member to it.netMinor }
        .toMutableList()
    val owing = balances.filter { it.netMinor < 0 }
        .sortedBy { it.netMinor }
        .map { it.member to -it.netMinor }
        .toMutableList()

    val transfers = mutableListOf<Transfer>()
    var creditor = 0
    var debtor = 0

    while (creditor < owed.size && debtor < owing.size) {
        val (creditorId, stillOwedToThem) = owed[creditor]
        val (debtorId, stillOwedByThem) = owing[debtor]
        val amount = minOf(stillOwedToThem, stillOwedByThem)

        if (amount > 0) transfers += Transfer(from = debtorId, to = creditorId, amountMinor = amount)

        owed[creditor] = creditorId to (stillOwedToThem - amount)
        owing[debtor] = debtorId to (stillOwedByThem - amount)
        if (owed[creditor].second == 0L) creditor++
        if (owing[debtor].second == 0L) debtor++
    }

    return transfers
}

private fun Item.approvedPaybacks(): List<Payback> =
    paybacks.filter { it.status == PaybackStatus.APPROVED }

/** Every person's portion of this item, honouring its split rule. */
fun Item.shares(): Map<MemberId, Long> =
    shares(amountMinor, sharedBy, split, id.value)

/** What [member] owes towards this item, or zero if they are not on its list. */
private fun Item.shareOf(member: MemberId): Long = shares()[member] ?: 0L

enum class ItemState { OPEN, ALL_SQUARE }

/**
 * An item is square once every sharer has covered their portion.
 *
 * The payer is excluded — they fronted the money, so their own share needs no payback.
 * Only approved paybacks count; a claim the payer hasn't agreed to leaves the item open.
 */
fun itemState(item: Item): ItemState {
    val shares = item.shares()
    val approvedByMember = item.approvedPaybacks()
        .groupBy { it.from }
        .mapValues { (_, theirs) -> theirs.sumOf { it.amountMinor } }

    val everyoneCovered = item.sharedBy
        .filter { it != item.payer }
        .all { sharer -> (approvedByMember[sharer] ?: 0L) >= (shares[sharer] ?: 0L) }

    return if (everyoneCovered) ItemState.ALL_SQUARE else ItemState.OPEN
}
