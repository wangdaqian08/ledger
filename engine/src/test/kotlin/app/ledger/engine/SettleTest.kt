package app.ledger.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class SettleTest {
    private fun m(name: String) = MemberId(name)

    private val dinnerItem = Item(
        id = ItemId(1),
        amountMinor = 20_000,
        payer = m("lucy"),
        sharedBy = listOf(m("lucy"), m("ben")),
    )

    private fun dinner(vararg paybacks: Payback) = Trip(
        members = listOf(m("lucy"), m("ben")),
        items = listOf(dinnerItem),
        paybacks = paybacks.toList(),
    )

    @Test
    fun `an approved payback squares off both sides`() {
        val settlement = settle(dinner(dinnerItem.repaidBy(m("ben"), 10_000, PaybackStatus.APPROVED)))

        assertEquals(0L, settlement.net(m("lucy")))
        assertEquals(0L, settlement.net(m("ben")))
    }

    @Test
    fun `a pending payback moves nothing at all`() {
        // S4. Claiming you paid is not the same as the payer agreeing you did.
        val settlement = settle(dinner(dinnerItem.repaidBy(m("ben"), 10_000, PaybackStatus.PENDING)))

        assertEquals(10_000L, settlement.net(m("lucy")))
        assertEquals(-10_000L, settlement.net(m("ben")))
    }

    @Test
    fun `a rejected payback moves nothing at all`() {
        val settlement = settle(dinner(dinnerItem.repaidBy(m("ben"), 10_000, PaybackStatus.REJECTED)))

        assertEquals(10_000L, settlement.net(m("lucy")))
        assertEquals(-10_000L, settlement.net(m("ben")))
    }

    @Test
    fun `a lone member who pays only for themselves comes out square`() {
        // One member, one item they alone share. The rotation inside the split does `salt % count`
        // with count == 1, which must land on the single share rather than wrap or divide by zero —
        // and the person owes themselves nothing, so their net is exactly zero.
        val solo = m("lucy")
        val trip = Trip(
            members = listOf(solo),
            items = listOf(
                Item(
                    id = ItemId(1),
                    amountMinor = 4_500,
                    payer = solo,
                    sharedBy = listOf(solo),
                ),
            ),
        )

        val settlement = settle(trip)

        assertEquals(0L, settlement.net(solo), "you owe yourself nothing")
        assertEquals(0L, settlement.balances.sumOf { it.netMinor })
    }

    @Test
    fun `the payer is owed every other sharer's portion`() {
        // Lucy fronts a $200 dinner for herself and Ben. Ben owes her his $100 half.
        val trip = Trip(
            members = listOf(m("lucy"), m("ben")),
            items = listOf(
                Item(
                    id = ItemId(1),
                    amountMinor = 20_000,
                    payer = m("lucy"),
                    sharedBy = listOf(m("lucy"), m("ben")),
                ),
            ),
        )

        val settlement = settle(trip)

        assertEquals(10_000L, settlement.net(m("lucy")))
        assertEquals(-10_000L, settlement.net(m("ben")))
    }
}
