package app.ledger.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * S2 — Lucy fronts a $200 dinner for four. The other three owe her $50 each, and the card
 * flips to all square the moment the third payback is approved.
 */
class ItemStateTest {
    private val lucy = MemberId("lucy")
    private val ben = MemberId("ben")
    private val amy = MemberId("amy")
    private val cara = MemberId("cara")

    private val dinnerItem = Item(
        id = ItemId(7),
        amountMinor = 20_000,
        payer = lucy,
        sharedBy = listOf(lucy, ben, amy, cara),
    )

    /** Paybacks now live on the trip, so the helper builds one and asks it for the state. */
    private fun dinner(vararg paybacks: Payback) = Trip(
        members = listOf(lucy, ben, amy, cara),
        items = listOf(dinnerItem),
        paybacks = paybacks.toList(),
    ).itemState(dinnerItem.id)

    private fun settled(who: MemberId) = dinnerItem.repaidBy(who, 5_000)

    private fun claimed(who: MemberId, amountMinor: Long, status: PaybackStatus) =
        dinnerItem.repaidBy(who, amountMinor, status)

    @Test
    fun `is open while nobody has paid the payer back`() {
        assertEquals(ItemState.OPEN, dinner())
    }

    @Test
    fun `is still open when only some have paid`() {
        assertEquals(ItemState.OPEN, dinner(settled(ben), settled(amy)))
    }

    @Test
    fun `goes all square once every sharer has covered their portion`() {
        // The payer's own share needs no payback — they already fronted the money.
        assertEquals(
            ItemState.ALL_SQUARE,
            dinner(settled(ben), settled(amy), settled(cara)),
        )
    }

    @Test
    fun `a pending payback does not make an item all square`() {
        assertEquals(
            ItemState.OPEN,
            dinner(settled(ben), settled(amy), claimed(cara, 5_000, PaybackStatus.PENDING)),
        )
    }

    @Test
    fun `part-paying does not count as covered`() {
        assertEquals(
            ItemState.OPEN,
            dinner(settled(ben), settled(amy), claimed(cara, 4_000, PaybackStatus.APPROVED)),
        )
    }

    @Test
    fun `an item the payer alone shares is square immediately`() {
        val soloCoffee = Item(
            id = ItemId(8),
            amountMinor = 450,
            payer = lucy,
            sharedBy = listOf(lucy),
        )

        assertEquals(
            ItemState.ALL_SQUARE,
            Trip(members = listOf(lucy), items = listOf(soloCoffee)).itemState(soloCoffee.id),
        )
    }

    /**
     * The cent that largest remainder hands to some people and not others is a real debt.
     *
     * §2 notes it in passing — the hotel paybacks were a round $76.92 rather than an exact
     * thirteenth, which is why Bob ends 4¢ clear of the other nine. Stated the other way round:
     * thirteen people sharing $1,000 do not all owe the same number of cents, so everybody paying
     * the same round amount leaves four of them a penny short, and the bill is *not* square.
     *
     * Found by clicking through the seeded demo, where both hotel bills stayed open and looked
     * wrong until the arithmetic was followed through.
     */
    @Test
    fun `everybody paying the same round amount can still leave the bill open`() {
        val payer = MemberId("bob")
        val others = (1..12).map { MemberId("friend-$it") }
        val everyone = listOf(payer) + others
        val hotel = Item(id = ItemId(3), amountMinor = 100_000, payer = payer, sharedBy = everyone)

        val shares = hotel.shares()
        val shortchanged = others.filter { shares.getValue(it) == 7_693L }
        assertEquals(true, shortchanged.isNotEmpty(), "expected largest remainder to hand out spare cents")

        // Everybody pays the round $76.92 that a human would actually transfer.
        val roundPayments = others.map { hotel.repaidBy(it, 7_692) }
        val trip = Trip(members = everyone, items = listOf(hotel), paybacks = roundPayments)

        assertEquals(ItemState.OPEN, trip.itemState(hotel.id), "a penny short is still short")

        // Paying the extra cent — and only that — squares it.
        val exact = roundPayments + shortchanged.map { hotel.repaidBy(it, 1) }
        assertEquals(ItemState.ALL_SQUARE, Trip(everyone, listOf(hotel), exact).itemState(hotel.id))
    }
}
