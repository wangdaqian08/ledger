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
    fun `one person fronting for three is paid back once by each of the other two`() {
        // AC-06 from the RevenueSplit PRD: Ann pays $120 shared by Ann, Ben and Cat. Ben and Cat owe
        // the same $40, so the tie between them breaks on roster order.
        val ann = m("ann")
        val ben = m("ben")
        val cat = m("cat")
        val trip = Trip(
            members = listOf(ann, ben, cat),
            items = listOf(Item(ItemId(1), 12_000, payer = ann, sharedBy = listOf(ann, ben, cat))),
        )

        assertEquals(listOf(Transfer(ben, ann, 4_000), Transfer(cat, ann, 4_000)), settle(trip).transfers)
    }

    @Test
    fun `exact pairs are matched in roster order when debts and credits tie`() {
        // Pia and Quinn are each owed $70; Xavi and Yan each owe $70. Nets are all the engine sees,
        // so who bought for whom does not decide the pairs — roster order does: the first debtor
        // takes the first creditor.
        val trip = Trip(
            members = listOf(m("pia"), m("quinn"), m("xavi"), m("yan")),
            items = listOf(
                Item(ItemId(1), 7_000, m("pia"), listOf(m("yan"))),
                Item(ItemId(2), 7_000, m("quinn"), listOf(m("xavi"))),
            ),
        )

        assertEquals(
            listOf(Transfer(m("xavi"), m("pia"), 7_000), Transfer(m("yan"), m("quinn"), 7_000)),
            settle(trip).transfers,
        )
    }

    @Test
    fun `the largest remaining debt and credit are re-chosen after every payment`() {
        // Amy owes $50 and Bob $40; Cal, Dee and Eli are owed $30 each, so there are no exact pairs.
        // After Amy pays Cal $30 she owes only $20, so Bob — now the larger debt — pays next. Walking
        // the lists in their starting order would have Amy pay Dee instead.
        val trip = Trip(
            members = listOf(m("amy"), m("bob"), m("cal"), m("dee"), m("eli")),
            items = listOf(
                Item(ItemId(1), 3_000, m("cal"), listOf(m("amy"))),
                Item(ItemId(2), 3_000, m("dee"), listOf(m("bob"))),
                Item(ItemId(3), 2_000, m("eli"), listOf(m("amy"))),
                Item(ItemId(4), 1_000, m("eli"), listOf(m("bob"))),
            ),
        )

        assertEquals(
            listOf(
                Transfer(m("amy"), m("cal"), 3_000),
                Transfer(m("bob"), m("dee"), 3_000),
                Transfer(m("amy"), m("eli"), 2_000),
                Transfer(m("bob"), m("eli"), 1_000),
            ),
            settle(trip).transfers,
        )
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
