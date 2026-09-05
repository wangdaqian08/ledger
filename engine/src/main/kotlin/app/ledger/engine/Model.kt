package app.ledger.engine

/** Identifies one item (a cost) within a trip. */
@JvmInline
value class ItemId(val value: Long)

enum class PaybackStatus { PENDING, APPROVED, REJECTED }

/**
 * Money handed from one member to another.
 *
 * With an [itemId] it is a repayment towards that item, and [to] is always that item's payer.
 * With no [itemId] it is a trip-level settlement from the Settle-up screen, where there is no
 * item to infer a recipient from — which is the whole reason [to] is stored rather than derived.
 *
 * Either way it only counts once the person owed has approved it — see [PaybackStatus].
 */
data class Payback(
    val from: MemberId,
    val to: MemberId,
    val amountMinor: Long,
    val status: PaybackStatus,
    val itemId: ItemId? = null,
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
    /** Evenly by default; the demo's SplitBar drag produces [SplitRule.Weighted]. */
    val split: SplitRule = SplitRule.Equal,
)

/** A repayment towards this item. The recipient can only ever be the person who fronted it. */
fun Item.repaidBy(
    from: MemberId,
    amountMinor: Long,
    status: PaybackStatus = PaybackStatus.APPROVED,
): Payback = Payback(from = from, to = payer, amountMinor = amountMinor, status = status, itemId = id)

data class Trip(
    val members: List<MemberId>,
    val items: List<Item>,
    /** Every repayment and settlement in the trip, item-linked or not. */
    val paybacks: List<Payback> = emptyList(),
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
    val approved = trip.approvedPaybacks()

    val balances = trip.members.map { member ->
        MemberBalance(
            member = member,
            paidOutMinor = trip.items.filter { it.payer == member }.sumOf { it.amountMinor } +
                approved.filter { it.from == member }.sumOf { it.amountMinor },
            // Whoever the money went to, item repayment or trip settlement alike.
            receivedBackMinor = approved.filter { it.to == member }.sumOf { it.amountMinor },
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
 *
 * Note the Settle-up screen does not use this: its rows are bilateral (see `owesBetween`).
 */
private fun suggestTransfers(balances: List<MemberBalance>): List<Transfer> {
    val owed = balances
        .filter { it.netMinor > 0 }
        .sortedByDescending { it.netMinor }
        .map { it.member to it.netMinor }
        .toMutableList()
    val owing = balances
        .filter { it.netMinor < 0 }
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

private fun Trip.approvedPaybacks(): List<Payback> =
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
 * Trip-level settlements are ignored: they clear a person's overall position, not one bill.
 */
fun Trip.itemState(itemId: ItemId): ItemState {
    val item = items.first { it.id == itemId }
    val shares = item.shares()

    val coveredByMember = approvedPaybacks()
        // Aimed at this item, and at the person actually owed on it — the payer. A payback towards
        // this item but paid to somebody else covers nothing here; the engine says so itself rather
        // than trusting every caller to only ever construct paybacks with `to = payer`.
        .filter { it.itemId == itemId && it.to == item.payer }
        .groupBy { it.from }
        .mapValues { (_, theirs) -> theirs.sumOf { it.amountMinor } }

    val everyoneCovered = item.sharedBy
        .filter { it != item.payer }
        .all { sharer -> (coveredByMember[sharer] ?: 0L) >= (shares[sharer] ?: 0L) }

    return if (everyoneCovered) ItemState.ALL_SQUARE else ItemState.OPEN
}

/**
 * What [a] owes [b], netted in both directions.
 *
 * Positive means [a] owes [b]. This is what the Settle-up screen's rows show — a person's
 * position with each other person, rather than the minimised transfer set from [settle].
 */
fun owesBetween(trip: Trip, a: MemberId, b: MemberId): Long {
    val approved = trip.approvedPaybacks()

    fun oneWay(debtor: MemberId, creditor: MemberId): Long =
        // The debtor's share of everything the creditor fronted...
        trip.items.filter { it.payer == creditor }.sumOf { it.shareOf(debtor) } -
            // ...less whatever they have already handed over and had approved, whether that
            // was filed against an item or settled straight from the Settle-up screen.
            approved.filter { it.from == debtor && it.to == creditor }.sumOf { it.amountMinor }

    return oneWay(a, b) - oneWay(b, a)
}

/** Any subset of the trip's members treated as one unit on the Settle-up screen. */
data class Family(val members: Set<MemberId>)

/**
 * One Family's position in a completed partition (§7b): its own net across the whole trip, and
 * its bilateral position with every *other* Family in the same partition — never with an
 * individual, and never the minimised transfer set ([suggestTransfers] is for a different screen
 * entirely — see the comment on it).
 */
data class FamilyBalance(val family: Family, val netMinor: Long, val betweenFamilies: Map<Family, Long>)

/**
 * Splits the whole trip into Families for the Settle-up screen (§7b).
 *
 * [explicitFamilies] is whatever the viewer has built so far — zero or more disjoint, non-empty
 * subsets of [Trip.members], in build order. Everyone else becomes their own one-person Family
 * automatically, so the result is always a complete partition of [Trip.members].
 *
 * Ephemeral by design: nothing here is persisted, so this is re-derived from scratch on every call
 * from whatever the caller currently holds — the same way [owesBetween] is re-derived per row
 * rather than stored.
 *
 * [settlement] defaults to a fresh [settle] so standalone callers/tests need not supply one, but a
 * caller with one already cached (`TripSnapshot`) must pass it through instead of paying for a
 * second pass.
 *
 * @throws IllegalArgumentException if an explicit Family is empty, if two explicit Families share
 *   a member, if a named member is not on the trip, or if the completed partition would have
 *   fewer than two Families (a single explicit Family naming everyone, leaving nobody to
 *   auto-singleton).
 */
fun partitionIntoFamilies(
    trip: Trip,
    explicitFamilies: List<Set<MemberId>>,
    settlement: Settlement = settle(trip),
): List<FamilyBalance> {
    val onTrip = trip.members.toSet()

    explicitFamilies.forEach { family ->
        require(family.isNotEmpty()) { "a family cannot be empty" }
        require(onTrip.containsAll(family)) { "a family can only contain members of this trip" }
    }

    val seen = mutableSetOf<MemberId>()
    explicitFamilies.forEach { family ->
        require((family intersect seen).isEmpty()) { "somebody is in two families at once" }
        seen += family
    }

    val singletons = (onTrip - seen).map { setOf(it) }
    val families = (explicitFamilies + singletons).map(::Family)
    require(families.size >= 2) { "a partition needs at least two families" }

    fun netOf(family: Family) = family.members.sumOf(settlement::net)

    fun owesBetweenFamilies(a: Family, b: Family) = a.members.sumOf { x ->
        b.members.sumOf { y ->
            owesBetween(
                trip,
                x,
                y,
            )
        }
    }

    return families.map { family ->
        FamilyBalance(
            family = family,
            netMinor = netOf(family),
            betweenFamilies = families.filter { it != family }.associateWith { owesBetweenFamilies(family, it) },
        )
    }
}
