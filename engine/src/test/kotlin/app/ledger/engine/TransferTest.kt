package app.ledger.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The end-of-trip "who pays who" list. Display only — the app never records that a
 * suggested transfer actually happened.
 */
class TransferTest {
    private fun m(name: String) = MemberId(name)

    /**
     * Two creditors and two debtors, so the greedy matching actually has a choice to make.
     *
     * Amy fronts $100 and Bob fronts $50, both shared only by Cara and Dan.
     * Nets land on amy +$100, bob +$50, cara −$75, dan −$75.
     */
    private fun lopsidedTrip() = Trip(
        members = listOf(m("amy"), m("bob"), m("cara"), m("dan")),
        items = listOf(
            Item(ItemId(100), 10_000, m("amy"), listOf(m("cara"), m("dan"))),
            Item(ItemId(101), 5_000, m("bob"), listOf(m("cara"), m("dan"))),
        ),
    )

    @Test
    fun `the lopsided trip really does produce the nets its comment claims`() {
        val settlement = settle(lopsidedTrip())

        assertEquals(10_000L, settlement.net(m("amy")))
        assertEquals(5_000L, settlement.net(m("bob")))
        assertEquals(-7_500L, settlement.net(m("cara")))
        assertEquals(-7_500L, settlement.net(m("dan")))
    }

    @Test
    fun `nobody owes anybody when the trip is square`() {
        val trip = Trip(members = listOf(m("amy"), m("bob")), items = emptyList())

        assertEquals(emptyList(), settle(trip).transfers)
    }

    @Test
    fun `each debtor pays the single person who fronted everything`() {
        // Lucy paid $200 for four. Three people owe her $50 each.
        val trip = Trip(
            members = listOf(m("lucy"), m("ben"), m("amy"), m("cara")),
            items = listOf(
                Item(
                    id = ItemId(1),
                    amountMinor = 20_000,
                    payer = m("lucy"),
                    sharedBy = listOf(m("lucy"), m("ben"), m("amy"), m("cara")),
                ),
            ),
        )

        val transfers = settle(trip).transfers

        assertEquals(3, transfers.size)
        assertTrue(transfers.all { it.to == m("lucy") })
        assertTrue(transfers.all { it.amountMinor == 5_000L })
    }

    @Test
    fun `clears everyone in at most one transfer fewer than there are people`() {
        val trip = lopsidedTrip()

        val transfers = settle(trip).transfers

        assertTrue(
            transfers.size <= trip.members.size - 1,
            "expected at most 3 transfers, got ${transfers.size}",
        )
    }

    @Test
    fun `transfers leave every single person square`() {
        val trip = lopsidedTrip()
        val settlement = settle(trip)

        settlement.balances.forEach { balance ->
            val sent = settlement.transfers.filter { it.from == balance.member }.sumOf { it.amountMinor }
            val received = settlement.transfers.filter { it.to == balance.member }.sumOf { it.amountMinor }

            assertEquals(
                0L,
                balance.netMinor + sent - received,
                "${balance.member.value} is not square after the suggested transfers",
            )
        }
    }

    @Test
    fun `money only ever moves from someone who owes to someone who is owed`() {
        val trip = lopsidedTrip()
        val settlement = settle(trip)

        settlement.transfers.forEach { transfer ->
            assertTrue(transfer.amountMinor > 0, "a transfer of zero or less is meaningless")
            assertTrue(settlement.net(transfer.from) < 0, "${transfer.from.value} does not owe anything")
            assertTrue(settlement.net(transfer.to) > 0, "${transfer.to.value} is not owed anything")
        }
    }
}
