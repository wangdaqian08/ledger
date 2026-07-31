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

    private fun dinner(vararg paybacks: Payback) = Item(
        id = ItemId(7),
        amountMinor = 20_000,
        payer = lucy,
        sharedBy = listOf(lucy, ben, amy, cara),
        paybacks = paybacks.toList(),
    )

    private fun settled(who: MemberId) = Payback(who, 5_000, PaybackStatus.APPROVED)

    @Test
    fun `is open while nobody has paid the payer back`() {
        assertEquals(ItemState.OPEN, itemState(dinner()))
    }

    @Test
    fun `is still open when only some have paid`() {
        assertEquals(ItemState.OPEN, itemState(dinner(settled(ben), settled(amy))))
    }

    @Test
    fun `goes all square once every sharer has covered their portion`() {
        // The payer's own share needs no payback — they already fronted the money.
        assertEquals(
            ItemState.ALL_SQUARE,
            itemState(dinner(settled(ben), settled(amy), settled(cara))),
        )
    }

    @Test
    fun `a pending payback does not make an item all square`() {
        assertEquals(
            ItemState.OPEN,
            itemState(dinner(settled(ben), settled(amy), Payback(cara, 5_000, PaybackStatus.PENDING))),
        )
    }

    @Test
    fun `part-paying does not count as covered`() {
        assertEquals(
            ItemState.OPEN,
            itemState(dinner(settled(ben), settled(amy), Payback(cara, 4_000, PaybackStatus.APPROVED))),
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

        assertEquals(ItemState.ALL_SQUARE, itemState(soloCoffee))
    }
}
