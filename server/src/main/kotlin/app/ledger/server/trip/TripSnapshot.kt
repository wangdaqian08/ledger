package app.ledger.server.trip

import app.ledger.engine.Item
import app.ledger.engine.ItemId
import app.ledger.engine.ItemState
import app.ledger.engine.MemberId
import app.ledger.engine.SplitRule
import app.ledger.engine.Trip
import app.ledger.engine.itemState
import app.ledger.engine.settle
import app.ledger.engine.shares
import app.ledger.server.item.ItemEntity
import app.ledger.server.item.ItemRepository
import app.ledger.server.item.ItemShareEntity
import app.ledger.server.item.ItemShareRepository
import app.ledger.server.item.SplitRuleName
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The salt an item's split rotates on, and therefore which member absorbs a spare cent.
 *
 * This mapping is permanent. Change it and every existing item redistributes its rounding by one
 * cent between people, silently, with no migration that could put it back. It is a pure function
 * of the item's identity — which is exactly what the spec means by `salt = itemId` — so it needs
 * no column and cannot drift.
 *
 * A negative value is fine: the engine rotates with `((index - offset) % count + count) % count`,
 * which is well defined for any Long.
 */
fun engineItemId(itemId: UUID): ItemId = ItemId(itemId.mostSignificantBits)

/**
 * One trip, loaded once, in the shape the engine understands.
 *
 * The engine owns every number; this class owns nothing but the translation. Anything that looks
 * like arithmetic belonging here is a sign a rule has escaped `engine/`.
 */
class TripSnapshot(
    val roster: List<TripMemberEntity>,
    val items: List<ItemEntity>,
    private val sharesByItem: Map<UUID, List<ItemShareEntity>>,
) {
    private val engineTrip: Trip = Trip(
        members = roster.map { MemberId(it.id.toString()) },
        items = items.map { it.toEngineItem(sharesByItem[it.id].orEmpty()) },
        // Paybacks arrive at build order step 6. Until then every item reads OPEN and every
        // balance is simply what people owe.
        paybacks = emptyList(),
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
            SplitRule.Weighted(shares.associate { MemberId(it.id.memberId.toString()) to (it.weight ?: 1) })
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
) {
    /**
     * Three queries for a whole trip, however many items it has. Loading shares per item would be
     * the classic N+1, and every screen in this app reads a trip whole.
     */
    fun load(tripId: UUID): TripSnapshot = TripSnapshot(
        roster = members.findAllByTripIdOrderByCreatedAt(tripId),
        items = items.findAllByTripIdOrderBySpentOnDescCreatedAtDesc(tripId),
        sharesByItem = shares.findAllByTripId(tripId).groupBy { it.id.itemId },
    )

    /**
     * Still three queries for any number of trips. GroupsHome shows your net on every group at
     * once, and doing that a trip at a time is the N+1 that turns a snappy home screen slow the
     * week somebody joins their tenth trip.
     */
    fun loadAll(tripIds: List<UUID>): Map<UUID, TripSnapshot> {
        if (tripIds.isEmpty()) return emptyMap()

        val rosters = members.findAllByTripIdInOrderByCreatedAt(tripIds).groupBy { it.tripId }
        val itemsByTrip = items.findAllByTripIdInOrderBySpentOnDescCreatedAtDesc(tripIds).groupBy { it.tripId }
        val sharesByTrip = shares.findAllByTripIdIn(tripIds).groupBy { it.tripId }

        return tripIds.associateWith { tripId ->
            TripSnapshot(
                roster = rosters[tripId].orEmpty(),
                items = itemsByTrip[tripId].orEmpty(),
                sharesByItem = sharesByTrip[tripId].orEmpty().groupBy { it.id.itemId },
            )
        }
    }
}
