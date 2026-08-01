package app.ledger.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The remaining scenarios from docs/specs/2026-07-31-ledger-design.md.
 *
 * These compose behaviour already driven out by the unit tests rather than driving new
 * production code — they exist so the spec's promises can't silently rot.
 */
class ScenariosTest {

    private fun m(name: String) = MemberId(name)

    // ---- S3 · the headcount drops ------------------------------------------------------

    @Test
    fun `S3 - someone who cancels is owed back what they already paid`() {
        val bob = m("bob")
        val dana = m("dana")
        val stayers = (1..8).map { m("stayer-$it") }

        // Bob fronted the $1,000 deposit and Dana had already sent him her tenth of it.
        // Then Dana cancelled, so she comes off the item's people list entirely.
        val hotel = Item(
            id = ItemId(1),
            amountMinor = 100_000,
            payer = bob,
            sharedBy = listOf(bob) + stayers,                  // Dana removed: 9, not 10
        )
        val trip = Trip(
            members = listOf(bob, dana) + stayers,
            items = listOf(hotel),
            paybacks = listOf(hotel.repaidBy(dana, 10_000)),
        )

        val settlement = settle(trip)

        // Dana owes nothing and is owed every cent back.
        assertEquals(10_000L, settlement.net(dana))
        // And the nine who went now carry a bigger share than they would have at ten.
        assertTrue(settlement.net(stayers.first()) < -10_000L)
        assertEquals(0L, settlement.balances.sumOf { it.netMinor })
    }

    // ---- S5 · rounding, including zero-decimal currencies -------------------------------

    @Test
    fun `S5 - a zero-decimal currency splits without a special case`() {
        // JPY has no minor unit, so 10,000 yen IS 10000 minor units. The engine never
        // needs to know that: it only ever divides whole minor units.
        val shares = splitEqually(10_000, listOf(m("a"), m("b"), m("c")))

        assertEquals(listOf(3_334L, 3_333L, 3_333L), shares.values.toList())
        assertEquals(10_000L, shares.values.sum())
    }

    @Test
    fun `S5 - awkward divisions still sum exactly, at every group size`() {
        (1..40).forEach { size ->
            val people = (1..size).map { m("p$it") }
            listOf(1L, 7L, 99L, 100L, 10_000L, 123_457L, 999_999L).forEach { total ->
                val shares = splitEqually(total, people, salt = size.toLong())
                assertEquals(total, shares.values.sum(), "$total split $size ways")
            }
        }
    }

    // ---- S6 · the property that must never break ----------------------------------------

    @Test
    fun `S6 - across a thousand random trips the column always sums to zero`() {
        (1..1_000).forEach { seed ->
            val trip = randomTrip(Random(seed))
            val settlement = settle(trip)

            assertEquals(
                0L,
                settlement.balances.sumOf { it.netMinor },
                "seed $seed: what everyone is owed must equal what everyone owes",
            )

            trip.items.forEach { item ->
                val shares = splitEqually(item.amountMinor, item.sharedBy, item.id.value)
                assertEquals(
                    item.amountMinor,
                    shares.values.sum(),
                    "seed $seed: item ${item.id.value} shares do not sum to its total",
                )
            }
        }
    }

    @Test
    fun `S6 - suggested transfers always leave every random trip square`() {
        (1..1_000).forEach { seed ->
            val settlement = settle(randomTrip(Random(seed)))

            settlement.balances.forEach { balance ->
                val sent = settlement.transfers.filter { it.from == balance.member }.sumOf { it.amountMinor }
                val received = settlement.transfers.filter { it.to == balance.member }.sumOf { it.amountMinor }

                assertEquals(
                    0L,
                    balance.netMinor + sent - received,
                    "seed $seed: ${balance.member.value} is not square afterwards",
                )
            }

            settlement.transfers.forEach {
                assertTrue(it.amountMinor > 0, "seed $seed: a transfer of zero or less")
                assertTrue(it.from != it.to, "seed $seed: ${it.from.value} paying themselves")
            }
        }
    }

    private fun randomTrip(random: Random): Trip {
        val members = (1..random.nextInt(2, 9)).map { m("member-$it") }

        val items = (1..random.nextInt(0, 7)).map { index ->
            // A non-empty people list, sometimes excluding the payer — someone can front a
            // bill they take no part in, like paying for everyone else's lift passes.
            Item(
                id = ItemId(index.toLong()),
                amountMinor = random.nextLong(1, 500_000),
                payer = members.random(random),
                sharedBy = members.filter { random.nextBoolean() }.ifEmpty { listOf(members.first()) },
            )
        }

        val repayments = items.flatMap { item ->
            item.sharedBy.filter { it != item.payer && random.nextBoolean() }.map {
                // Deliberately unconstrained: under- and over-payments both happen.
                item.repaidBy(it, random.nextLong(1, 200_000), PaybackStatus.entries.random(random))
            }
        }

        // Trip-level settlements between any two members, with no item behind them.
        val settlements = (1..random.nextInt(0, 4)).mapNotNull {
            val from = members.random(random)
            val to = members.random(random)
            if (from == to) null
            else Payback(from, to, random.nextLong(1, 100_000), PaybackStatus.entries.random(random))
        }

        return Trip(members = members, items = items, paybacks = repayments + settlements)
    }
}
