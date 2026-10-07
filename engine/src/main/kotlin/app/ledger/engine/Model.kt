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
    return Settlement(balances, suggestTransfers(balances.map { it.member to it.netMinor }, ::Transfer))
}

/**
 * Who pays whom to clear everyone — the Settle-up screen's "By minimum transfer" list (§7a), the
 * PRD's *Fewest transfers*. A party is whoever is treated as one: a person for [settle], a Family
 * for [familyTransfers]. This is the only copy of the algorithm, generic over the party, because
 * the two lists must agree exactly — with no Families built, the Family plan *is* the per-person
 * plan — and two copies would be two chances to drift. Two passes over every party that is not
 * already square:
 *
 * 1. **Exact pairs first.** Each debtor, largest debt first, pairs with the first still-unmatched
 *    creditor owed exactly that amount. One transfer then settles two parties at once, which is the
 *    move plain largest-against-largest misses: on the weekend-away trip (spec S8) greedy alone
 *    needs four transfers, and this needs three.
 * 2. **Then largest against largest.** Whoever still owes most pays whoever is still owed most,
 *    the smaller of the two amounts, re-choosing both after every step until nobody is left.
 *
 * Every tie breaks on the order of [nets] — roster order for people, partition order for Families —
 * and every choice below keeps the first of equals, so one input always yields the same list, in
 * the same order: the exact-pair transfers, then the greedy ones.
 *
 * Each transfer zeroes at least one party and the last zeroes two, so n parties that are not square
 * need at most n − 1 transfers. That bound is the whole promise. It is **not** guaranteed to be the
 * absolute minimum: that means finding the most disjoint zero-sum groups, a subset-sum search, and
 * a trip whose debts happen to split into two such groups can be cleared in fewer than this finds.
 * Money only ever moves from a party that owes to one that is owed — nobody is asked to forward
 * money they are themselves waiting on.
 *
 * [nets] must hold each party once, and sum to zero — invariant 1, which a Family partition inherits
 * from the people in it. [transfer] builds each line, so the caller's own type comes back.
 */
private fun <P, T> suggestTransfers(
    nets: List<Pair<P, Long>>,
    transfer: (from: P, to: P, amountMinor: Long) -> T,
): List<T> {
    // What each party still owes, or is still owed, in the order of [nets] — the order every tie uses.
    val owing = nets.filter { it.second < 0 }.associateTo(LinkedHashMap()) { (party, net) -> party to -net }
    val owed = nets.filter { it.second > 0 }.associateTo(LinkedHashMap()) { (party, net) -> party to net }
    val transfers = mutableListOf<T>()

    fun pay(debtor: P, creditor: P, amountMinor: Long) {
        transfers += transfer(debtor, creditor, amountMinor)
        // Whoever reaches zero drops out. Nobody is paid past zero, so nobody ever changes side.
        owing.computeIfPresent(debtor) { _, left -> (left - amountMinor).takeIf { it > 0 } }
        owed.computeIfPresent(creditor) { _, left -> (left - amountMinor).takeIf { it > 0 } }
    }

    // 1. Exact pairs. A stable sort, so equal debts keep their order; the creditors scanned are all
    //    owed the same amount, so the first in order is also the first by size.
    owing.entries.sortedByDescending { it.value }.map { it.key to it.value }.forEach { (debtor, debt) ->
        val creditor = owed.entries.firstOrNull { it.value == debt }?.key ?: return@forEach
        pay(debtor, creditor, debt)
    }

    // 2. Largest against largest, re-chosen every step. maxBy keeps the first of equals, and both
    //    maps are still in their original order. Invariant 1 empties both sides together; the second
    //    guard only stops a broken one from looping forever.
    while (owing.isNotEmpty() && owed.isNotEmpty()) {
        val (debtor, debt) = owing.entries.maxBy { it.value }
        val (creditor, credit) = owed.entries.maxBy { it.value }
        pay(debtor, creditor, minOf(debt, credit))
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
 * Positive means [a] owes [b]. This is what the Settle-up screen's "By person" rows show — a
 * person's position with each other person, rather than the minimised transfer set from [settle]
 * that its "By minimum transfer" mode lists. Once people pay by that list these rows can cancel
 * without reaching zero, which is why "square" is a net of zero and not every row at zero.
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

/**
 * One person's line of "How it adds up" on the Settle-up screen (§3): the columns a person can add
 * up by hand to reach their balance, `fronted − share + settled = net`.
 */
data class MemberBreakdown(
    val member: MemberId,
    /** The full amount of every item this person paid for. */
    val frontedMinor: Long,
    /** Their portion of every item, by its own split rule — what [settle] charges them. */
    val shareMinor: Long,
    /** Approved paybacks sent less approved paybacks received, item-linked and trip-level alike. */
    val settledMinor: Long,
    /** The settlement's own net for this person, not this row re-added: that is what the row checks. */
    val netMinor: Long,
    /** How many other people this person has a non-zero By person row with ([owesBetween]). */
    val bilateralCount: Int,
    /** How many lines of the fewest-transfers plan ([Settlement.transfers]) this person is on. */
    val planCount: Int,
)

/**
 * "How it adds up" (§3) — the whole trip's [MemberBreakdown] rows in roster order, with totals.
 *
 * The totals are here rather than left to whoever draws the table: fronted and share both total the
 * group spend, settled and net both total zero, [bilateralPairs] is the non-zero By person pairs
 * (each counted once, though it shows on two people's rows) and [planTransfers] is the plan's size.
 */
data class Breakdown(
    val rows: List<MemberBreakdown>,
    val frontedMinor: Long,
    val shareMinor: Long,
    val settledMinor: Long,
    val netMinor: Long,
    val bilateralPairs: Int,
    val planTransfers: Int,
)

/**
 * The figures behind every balance, laid out so a person can check the maths by hand — which is
 * why it exists. Each column is the engine's own number: net is [settlement]'s, share is what it
 * charged, the counts are its plan and [owesBetween]'s rows. Nothing is re-derived to agree with
 * itself, so a row that failed to tie out would be a real disagreement, and a client that summed or
 * recomputed any of it would be checking its own arithmetic instead of the engine's.
 *
 * Only approved paybacks are settled, the same filter [settle] applies. [settlement] defaults to a
 * fresh [settle]; a caller holding one already (`TripSnapshot`) passes it through.
 */
fun breakdown(trip: Trip, settlement: Settlement = settle(trip)): Breakdown {
    val approved = trip.approvedPaybacks()

    // Each unordered pair once: a's row with b is exactly minus b's with a, so asking both is waste.
    val nonZeroPairs = trip.members.flatMapIndexed { index, a ->
        trip.members
            .drop(index + 1)
            .filter { b -> owesBetween(trip, a, b) != 0L }
            .map { b -> setOf(a, b) }
    }

    val rows = settlement.balances.map { balance ->
        val member = balance.member
        MemberBreakdown(
            member = member,
            frontedMinor = trip.items.filter { it.payer == member }.sumOf { it.amountMinor },
            shareMinor = balance.owedMinor,
            settledMinor = approved.filter { it.from == member }.sumOf { it.amountMinor } -
                approved.filter { it.to == member }.sumOf { it.amountMinor },
            netMinor = balance.netMinor,
            bilateralCount = nonZeroPairs.count { member in it },
            planCount = settlement.transfers.count { it.from == member || it.to == member },
        )
    }

    return Breakdown(
        rows = rows,
        frontedMinor = rows.sumOf { it.frontedMinor },
        shareMinor = rows.sumOf { it.shareMinor },
        settledMinor = rows.sumOf { it.settledMinor },
        netMinor = rows.sumOf { it.netMinor },
        bilateralPairs = nonZeroPairs.size,
        planTransfers = settlement.transfers.size,
    )
}

/** Any subset of the trip's members treated as one unit on the Settle-up screen. */
data class Family(val members: Set<MemberId>)

/**
 * One Family's position in a completed partition (§7b): its own net across the whole trip, and
 * its bilateral position with every *other* Family in the same partition — never with an
 * individual. [betweenFamilies] is what actually passed between two Families, which is what the
 * Family cards show, and they stay bilateral. The minimised plan between Families exists too, but
 * it is a different thing: [familyTransfers], the Settle-up screen's "By minimum transfer" mode
 * with Families built, read off [netMinor] alone and free to pair Families that owe each other
 * nothing at all.
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

    fun owesBetweenFamilies(a: Family, b: Family) =
        a.members.sumOf { x -> b.members.sumOf { y -> owesBetween(trip, x, y) } }

    return families.map { family ->
        FamilyBalance(
            family = family,
            netMinor = family.members.sumOf(settlement::net),
            betweenFamilies = families.filter { it != family }.associateWith { owesBetweenFamilies(family, it) },
        )
    }
}

/** One line of the plan between Families: [from] pays [to] exactly [amountMinor], one party each. */
data class FamilyTransfer(val from: Family, val to: Family, val amountMinor: Long)

/**
 * The Settle-up screen's "By minimum transfer" list once the viewer has built Families (§7a): each
 * Family in [partition] is one party, so it pays, or is paid, once — "Ben & Cat pay Ann $200" — and
 * whatever its members owe one another is theirs to square among themselves, which is the reason
 * anybody builds a Family.
 *
 * The same algorithm as [settle]'s per-person list, run over Family nets instead of personal ones,
 * so everything promised there holds per Family: every Family's net is cleared, in at most one
 * transfer fewer than the Families that are not square, with money only ever moving from a Family
 * that owes to one that is owed. Ties break on partition order — explicit Families in build order,
 * then the automatic one-person Families in roster order, exactly as [partitionIntoFamilies]
 * returns them. With nothing built that order *is* roster order, so the plan is [settle]'s list line
 * for line; that is why there is one algorithm and not two.
 *
 * Reads nothing but each [FamilyBalance.netMinor], which already came from the settlement the
 * partition was built on — so a caller holding a cached one pays for no second pass over the trip.
 *
 * [partition] is the whole list [partitionIntoFamilies] returned, unfiltered and unreordered: a
 * Family left out would leave its money unaccounted for, and a reordered list would break ties
 * differently from the order the viewer built them in.
 */
fun familyTransfers(partition: List<FamilyBalance>): List<FamilyTransfer> =
    suggestTransfers(partition.map { it.family to it.netMinor }, ::FamilyTransfer)
