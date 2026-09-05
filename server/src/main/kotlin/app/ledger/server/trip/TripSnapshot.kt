package app.ledger.server.trip

import app.ledger.engine.FamilyBalance
import app.ledger.engine.Item
import app.ledger.engine.ItemId
import app.ledger.engine.ItemState
import app.ledger.engine.MemberId
import app.ledger.engine.Payback
import app.ledger.engine.PaybackStatus
import app.ledger.engine.SplitRule
import app.ledger.engine.Trip
import app.ledger.engine.itemState
import app.ledger.engine.owesBetween
import app.ledger.engine.partitionIntoFamilies
import app.ledger.engine.settle
import app.ledger.engine.shares
import app.ledger.server.item.ItemEntity
import app.ledger.server.item.ItemRepository
import app.ledger.server.item.ItemShareEntity
import app.ledger.server.item.ItemShareRepository
import app.ledger.server.item.SplitRuleName
import app.ledger.server.payback.PaybackEntity
import app.ledger.server.payback.PaybackRepository
import app.ledger.server.payback.PaybackStatusName
import app.ledger.server.receipt.ReceiptRepository
import java.util.*
import org.springframework.stereotype.Component

/**
 * The item's identity inside the engine: both the salt its split rotates on and the key its
 * paybacks are matched by.
 *
 * **Both halves of the UUID, folded together.** An earlier version took only
 * `mostSignificantBits`, which is fine for random v4 ids and wrong for anything structured — two
 * ids differing solely in their low bits mapped to the same engine item, so one bill's paybacks
 * were counted against the other and a half-paid bill read ALL_SQUARE. That is not hypothetical:
 * UUIDv7 carries a millisecond timestamp in the high bits, so two expenses added in the same
 * moment would collide, and the client is the thing that mints these ids.
 *
 * This mapping is permanent once anything is deployed. Change it and every existing item
 * redistributes its rounding by a cent, silently, with no migration that could put it back.
 *
 * A negative value is fine: the engine rotates with `((index - offset) % count + count) % count`,
 * which is well defined for any Long.
 */
fun engineItemId(itemId: UUID): ItemId = ItemId(itemId.mostSignificantBits xor itemId.leastSignificantBits)

/**
 * One trip, loaded once, in the shape the engine understands.
 *
 * The engine owns every number; this class owns nothing but the translation. Anything that looks
 * like arithmetic belonging here is a sign a rule has escaped `engine/`.
 */
class TripSnapshot(
    val roster: List<TripMemberEntity>,
    val items: List<ItemEntity>,
    val paybacks: List<PaybackEntity>,
    private val sharesByItem: Map<UUID, List<ItemShareEntity>>,
    private val receiptVersionByItem: Map<UUID, String>,
) {
    private val engineTrip: Trip = Trip(
        members = roster.map { MemberId(it.id.toString()) },
        items = items.map { it.toEngineItem(sharesByItem[it.id].orEmpty()) },
        // Every payback, whatever its status. The engine filters to APPROVED itself — passing only
        // the approved ones would work today and quietly hide the rule the moment anything here
        // needed to reason about a pending claim.
        paybacks = paybacks.map { it.toEnginePayback() },
    )

    private val settlement by lazy { settle(engineTrip) }

    fun netFor(memberId: UUID): Long = settlement.net(MemberId(memberId.toString()))

    /** Every person's portion of one item, in the same order as its people list. */
    fun sharesOf(item: ItemEntity): Map<UUID, Long> {
        val shares = shares(
            totalMinor = item.amountMinor,
            members = sharesByItem[item.id].orEmpty().map { MemberId(it.id.memberId.toString()) },
            rule = item.toSplitRule(sharesByItem[item.id].orEmpty()),
            salt = engineItemId(item.id).value,
        )
        return shares.mapKeys { UUID.fromString(it.key.value) }
    }

    fun stateOf(item: ItemEntity): ItemState = engineTrip.itemState(engineItemId(item.id))

    /** The stored split *inputs* — weights and exact amounts — keyed by member. */
    fun itemShareInputs(item: ItemEntity): Map<UUID, ItemShareEntity> =
        sharesByItem[item.id].orEmpty().associateBy { it.id.memberId }

    fun memberFor(userId: UUID): TripMemberEntity? = roster.firstOrNull { it.userId == userId }

    /** The version of one item's receipt image, or null when no image is attached. */
    fun receiptVersionFor(item: ItemEntity): String? = receiptVersionByItem[item.id]

    /** Every payback filed against one bill, newest last, whatever its status. */
    fun paybacksFor(item: ItemEntity): List<PaybackEntity> = paybacks.filter { it.itemId == item.id }

    /**
     * What [a] owes [b], netted both ways — one Settle-up row (§7a). Positive means [a] owes [b].
     *
     * Not the minimised transfer set: `settle()` computes those and they are property-tested, but
     * they pair up people who are not party to each other's rows, which is not what this screen
     * shows.
     */
    fun owesBetween(a: UUID, b: UUID): Long =
        // Qualified: this method's own name would otherwise shadow the engine function it calls.
        owesBetween(engineTrip, MemberId(a.toString()), MemberId(b.toString()))

    fun families(explicitFamilies: List<Set<UUID>>): List<FamilyBalance> = partitionIntoFamilies(
        engineTrip,
        explicitFamilies.map { it.mapTo(mutableSetOf()) { id -> MemberId(id.toString()) } },
        settlement,
    )

    private fun PaybackEntity.toEnginePayback() = Payback(
        from = MemberId(fromMemberId.toString()),
        to = MemberId(toMemberId.toString()),
        amountMinor = amountMinor,
        status = when (status) {
            PaybackStatusName.PENDING -> PaybackStatus.PENDING
            PaybackStatusName.APPROVED -> PaybackStatus.APPROVED
            PaybackStatusName.REJECTED -> PaybackStatus.REJECTED
        },
        itemId = itemId?.let(::engineItemId),
    )

    private fun ItemEntity.toEngineItem(shares: List<ItemShareEntity>) = Item(
        id = engineItemId(id),
        amountMinor = amountMinor,
        payer = MemberId(payerMemberId.toString()),
        sharedBy = shares.map { MemberId(it.id.memberId.toString()) },
        split = toSplitRule(shares),
    )

    private fun ItemEntity.toSplitRule(shares: List<ItemShareEntity>): SplitRule = when (splitRule) {
        SplitRuleName.EQUAL -> {
            SplitRule.Equal
        }

        SplitRuleName.WEIGHTED -> {
            // A missing weight is 0, not 1 — the same reading validation uses ("on the bill, owing
            // nothing") and the same default the engine itself applies. Defaulting to 1 here let an
            // omitted weight validate as zero and then be charged an equal share.
            SplitRule.Weighted(shares.associate { MemberId(it.id.memberId.toString()) to (it.weight ?: 0) })
        }

        SplitRuleName.EXACT -> {
            SplitRule.Exact(shares.associate { MemberId(it.id.memberId.toString()) to (it.exactAmountMinor ?: 0L) })
        }
    }
}

@Component
class TripSnapshots(
    private val members: TripMemberRepository,
    private val items: ItemRepository,
    private val shares: ItemShareRepository,
    private val paybacks: PaybackRepository,
    private val receipts: ReceiptRepository,
) {
    /**
     * Five queries for a whole trip, however many items it has. Loading shares or receipts per
     * item would be the classic N+1, and every screen in this app reads a trip whole.
     */
    fun load(tripId: UUID): TripSnapshot = TripSnapshot(
        roster = members.findAllByTripIdOrderByCreatedAt(tripId),
        items = items.findAllByTripIdOrderBySpentOnDescCreatedAtDesc(tripId),
        paybacks = paybacks.findAllByTripIdOrderByCreatedAt(tripId),
        sharesByItem = shares.findAllByTripIdOrderByPosition(tripId).groupBy { it.id.itemId },
        receiptVersionByItem = receipts.findAllByTripId(tripId).associate { it.itemId to it.version },
    )

    /**
     * Still five queries for any number of trips. GroupsHome shows your net on every group at
     * once, and doing that a trip at a time is the N+1 that turns a snappy home screen slow the
     * week somebody joins their tenth trip.
     */
    fun loadAll(tripIds: List<UUID>): Map<UUID, TripSnapshot> {
        if (tripIds.isEmpty()) return emptyMap()

        val rosters = members.findAllByTripIdInOrderByCreatedAt(tripIds).groupBy { it.tripId }
        val itemsByTrip = items.findAllByTripIdInOrderBySpentOnDescCreatedAtDesc(tripIds).groupBy { it.tripId }
        val sharesByTrip = shares.findAllByTripIdInOrderByPosition(tripIds).groupBy { it.tripId }
        val paybacksByTrip = paybacks.findAllByTripIdInOrderByCreatedAt(tripIds).groupBy { it.tripId }
        val receiptsByTrip = receipts.findAllByTripIdIn(tripIds).groupBy { it.tripId }

        return tripIds.associateWith { tripId ->
            TripSnapshot(
                roster = rosters[tripId].orEmpty(),
                items = itemsByTrip[tripId].orEmpty(),
                paybacks = paybacksByTrip[tripId].orEmpty(),
                sharesByItem = sharesByTrip[tripId].orEmpty().groupBy { it.id.itemId },
                receiptVersionByItem = receiptsByTrip[tripId].orEmpty().associate { it.itemId to it.version },
            )
        }
    }
}
