package app.ledger.engine

/** Identifies one item (a cost) within a trip. */
@JvmInline
value class ItemId(val value: Long)

/**
 * Money that left the group, paid by exactly one person.
 *
 * [sharedBy] is the list of people the cost is split equally between. It stays editable for
 * the life of the trip — correcting it is the whole mechanism by which a changed headcount
 * flows through to everyone's balance.
 */
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

data class Item(
    val id: ItemId,
    val amountMinor: Long,
    val payer: MemberId,
    val sharedBy: List<MemberId>,
    val paybacks: List<Payback> = emptyList(),
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

data class Settlement(val balances: List<MemberBalance>) {
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
    return Settlement(balances)
}

private fun Item.approvedPaybacks(): List<Payback> =
    paybacks.filter { it.status == PaybackStatus.APPROVED }

/** What [member] owes towards this item, or zero if they are not on its list. */
private fun Item.shareOf(member: MemberId): Long =
    splitEqually(amountMinor, sharedBy, id.value)[member] ?: 0L
