package app.ledger.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Settle-up screen's rows — see §7a of the spec.
 *
 * Rows are bilateral: what you owe each person, or they owe you. Not the globally minimised
 * transfer set `settle()` produces, which emits pairs the viewer isn't party to and which
 * would have no row on that screen.
 */
class BilateralTest {
    private val lucy = MemberId("lucy")
    private val ben = MemberId("ben")
    private val amy = MemberId("amy")

    @Test
    fun `a sharer owes the payer their portion`() {
        val dinner = Item(ItemId(1), 20_000, payer = lucy, sharedBy = listOf(lucy, ben))
        val trip = Trip(members = listOf(lucy, ben), items = listOf(dinner))

        assertEquals(10_000L, owesBetween(trip, ben, lucy))
    }

    @Test
    fun `the same debt read the other way round is its negative`() {
        val dinner = Item(ItemId(1), 20_000, payer = lucy, sharedBy = listOf(lucy, ben))
        val trip = Trip(members = listOf(lucy, ben), items = listOf(dinner))

        assertEquals(-10_000L, owesBetween(trip, lucy, ben))
    }

    @Test
    fun `an approved repayment clears it`() {
        val dinner = Item(ItemId(1), 20_000, payer = lucy, sharedBy = listOf(lucy, ben))
        val trip = Trip(
            members = listOf(lucy, ben),
            items = listOf(dinner),
            paybacks = listOf(dinner.repaidBy(ben, 10_000)),
        )

        assertEquals(0L, owesBetween(trip, ben, lucy))
    }

    @Test
    fun `a pending repayment clears nothing`() {
        val dinner = Item(ItemId(1), 20_000, payer = lucy, sharedBy = listOf(lucy, ben))
        val trip = Trip(
            members = listOf(lucy, ben),
            items = listOf(dinner),
            paybacks = listOf(dinner.repaidBy(ben, 10_000, PaybackStatus.PENDING)),
        )

        assertEquals(10_000L, owesBetween(trip, ben, lucy))
    }

    @Test
    fun `a trip-level settlement clears it just as well`() {
        val dinner = Item(ItemId(1), 20_000, payer = lucy, sharedBy = listOf(lucy, ben))
        val trip = Trip(
            members = listOf(lucy, ben),
            items = listOf(dinner),
            paybacks = listOf(Payback(ben, lucy, 10_000, PaybackStatus.APPROVED)),
        )

        assertEquals(0L, owesBetween(trip, ben, lucy))
    }

    @Test
    fun `debts in both directions net off against each other`() {
        // Lucy fronts dinner for the two of them; Ben fronts the taxi for the two of them.
        val dinner = Item(ItemId(1), 20_000, payer = lucy, sharedBy = listOf(lucy, ben))
        val taxi = Item(ItemId(2), 6_000, payer = ben, sharedBy = listOf(lucy, ben))
        val trip = Trip(members = listOf(lucy, ben), items = listOf(dinner, taxi))

        // Ben owes $100 for dinner, Lucy owes $30 for the taxi → Ben owes $70, one row.
        assertEquals(7_000L, owesBetween(trip, ben, lucy))
    }

    @Test
    fun `people who never shared anything owe each other nothing`() {
        val dinner = Item(ItemId(1), 20_000, payer = lucy, sharedBy = listOf(lucy, ben))
        val trip = Trip(members = listOf(lucy, ben, amy), items = listOf(dinner))

        assertEquals(0L, owesBetween(trip, amy, ben))
    }

    /**
     * The identity that makes the screen honest: the rows on your Settle-up sheet must add
     * up to the figure on the hero card above them. In the supplied screenshot that is
     * −39.10 − 46.00 + 42.30 = −42.80.
     */
    @Test
    fun `everyone's bilateral rows add up to their net`() {
        (1..500).forEach { seed ->
            val trip = randomTrip(Random(seed))
            val settlement = settle(trip)

            trip.members.forEach { person ->
                val rowTotal = trip.members
                    .filter { it != person }
                    .sumOf { other -> owesBetween(trip, person, other) }

                assertEquals(
                    -settlement.net(person),
                    rowTotal,
                    "seed $seed: ${person.value}'s rows don't add up to the hero card",
                )
            }
        }
    }

    /**
     * The supplied Settle-up screenshot, reconstructed from items that would produce it:
     * you owe Mei $39.10, you owe Sam $46.00, Ade owes you $42.30 — and the hero card
     * above those three rows reads YOU OWE $42.80.
     */
    @Test
    fun `reproduces the settle-up screenshot`() {
        val you = MemberId("you")
        val mei = MemberId("mei")
        val sam = MemberId("sam")
        val ade = MemberId("ade")

        val trip = Trip(
            members = listOf(you, mei, sam, ade),
            items = listOf(
                Item(ItemId(1), 7_820, payer = mei, sharedBy = listOf(you, mei)),
                Item(ItemId(2), 9_200, payer = sam, sharedBy = listOf(you, sam)),
                Item(ItemId(3), 8_460, payer = you, sharedBy = listOf(you, ade)),
            ),
        )

        assertEquals(3_910L, owesBetween(trip, you, mei))    // you owe    $39.10
        assertEquals(4_600L, owesBetween(trip, you, sam))    // you owe    $46.00
        assertEquals(-4_230L, owesBetween(trip, you, ade))   // owes you   $42.30

        // The rows have to add up to the card above them, or the screen is lying.
        assertEquals(-4_280L, settle(trip).net(you))         // YOU OWE    $42.80
    }

    private fun randomTrip(random: Random): Trip {
        val members = (1..random.nextInt(2, 7)).map { MemberId("member-$it") }

        val items = (1..random.nextInt(0, 6)).map { index ->
            Item(
                id = ItemId(index.toLong()),
                amountMinor = random.nextLong(1, 500_000),
                payer = members.random(random),
                sharedBy = members.filter { random.nextBoolean() }.ifEmpty { listOf(members.first()) },
            )
        }

        val repayments = items.flatMap { item ->
            item.sharedBy.filter { it != item.payer && random.nextBoolean() }.map {
                item.repaidBy(it, random.nextLong(1, 200_000), PaybackStatus.entries.random(random))
            }
        }

        val settlements = (1..random.nextInt(0, 4)).mapNotNull {
            val from = members.random(random)
            val to = members.random(random)
            if (from == to) {
                null
            } else {
                Payback(from, to, random.nextLong(1, 100_000), PaybackStatus.entries.random(random))
            }
        }

        return Trip(members = members, items = items, paybacks = repayments + settlements)
    }
}
