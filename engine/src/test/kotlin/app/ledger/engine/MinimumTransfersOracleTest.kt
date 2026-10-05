package app.ledger.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Holds the fewest-transfers plan to the true minimum, found by exhaustive search (§4).
 *
 * The plan promises at most one transfer fewer than the people who are not square; it does not
 * promise the absolute minimum. That minimum is (parties not square) − (the most disjoint groups
 * whose nets each sum to zero): each such group can be cleared in one transfer fewer than its size,
 * and no plan can do better, because the people a plan connects always form zero-sum groups. Finding
 * the most groups is a subset search, so it lives here in test sources as an oracle, never in
 * production, and only for trips small enough to search.
 *
 * What it proves: the plan is never below the minimum (it could not be, if it really clears every
 * net), it *is* the minimum whenever that is provable from how the plan is built, and the case where
 * it falls short is pinned rather than hidden.
 *
 * Two sweeps. [randomTrip]'s amounts run to the cent, so its nets almost never fall into a zero-sum
 * group other than an exact pair — on its own it would never reach the cases the oracle is for. The
 * round-amount trips, whole $10s between pairs of people, fall into such groups often.
 */
class MinimumTransfersOracleTest {
    private val seeds = 1..2_000

    @Test
    fun `the oracle itself gives the known answers`() {
        assertEquals(0, minimumTransfers(emptyList()))
        assertEquals(0, minimumTransfers(listOf(0L, 0L)))
        assertEquals(1, minimumTransfers(listOf(500L, -500L)))
        assertEquals(3, minimumTransfers(listOf(100L, 100L, 100L, -300L)))
        // S8: {+30, −30} and {+25, +25, −50}.
        assertEquals(3, minimumTransfers(listOf(2_500L, 2_500L, 3_000L, -5_000L, -3_000L)))
        // The known gap below: {+40, −20, −20} and {+30, +30, −60}.
        assertEquals(4, minimumTransfers(listOf(4_000L, 3_000L, 3_000L, -6_000L, -2_000L, -2_000L)))
    }

    @Test
    fun `no plan ever beats the true minimum`() {
        // A plan that did would not really clear every net — or the oracle would be wrong.
        sweep { trip, settlement, nets ->
            val optimum = minimumTransfers(nets)
            assertTrue(
                settlement.transfers.size >= optimum,
                "$trip: ${settlement.transfers.size} transfers, below the true minimum of $optimum",
            )
        }
    }

    @Test
    fun `the plan is the true minimum whenever what is left after exact pairs has no smaller zero-sum group`() {
        // Taking an exact pair is always safe: some optimal grouping has {+a, −a} as a group of its
        // own. And on a set with no zero-sum group smaller than itself, every plan connects all k
        // parties, so needs k − 1 — exactly what largest-against-largest uses. Both passes are then
        // optimal, so the plan must equal the oracle on every such trip.
        var provable = 0
        var searched = 0
        sweep { trip, settlement, nets ->
            searched++
            if (hasSmallerZeroSumGroup(withoutExactPairs(nets))) return@sweep

            provable++
            assertEquals(minimumTransfers(nets), settlement.transfers.size, "$trip: nets $nets")
        }
        println("Optimality provable, and held, on $provable of $searched trips searched")
        assertTrue(provable > 0, "the property never fired, so it proved nothing")
        assertTrue(provable < searched, "nothing fell outside it, so the round-amount sweep is not doing its job")
    }

    /**
     * The case the exact-pairs pass cannot see: no debt equals any credit, but the nets still split
     * into two zero-sum groups — {+40, −20, −20} and {+30, +30, −60} — clearable in two transfers
     * each. Largest against largest starts by pairing −60 with +40 and so crosses the groups, which
     * costs a fifth transfer. Pinned so the gap is known rather than discovered; an exact solver
     * (the subset search this oracle runs) would close it, at a cost that grows exponentially.
     */
    @Test
    fun `the known gap - two zero-sum groups with no exact pair take one transfer more than the minimum`() {
        val (a, b, c) = listOf(MemberId("a"), MemberId("b"), MemberId("c"))
        val (d, e, f) = listOf(MemberId("d"), MemberId("e"), MemberId("f"))
        val trip = Trip(
            members = listOf(a, b, c, d, e, f),
            items = listOf(
                Item(ItemId(1), 4_000, payer = a, sharedBy = listOf(e, f)),               // e, f owe a $20 each
                Item(ItemId(2), 3_000, payer = b, sharedBy = listOf(d)),                  // d owes b $30
                Item(ItemId(3), 3_000, payer = c, sharedBy = listOf(d)),                  // and c $30
            ),
        )
        val settlement = settle(trip)

        assertEquals(
            listOf(4_000L, 3_000L, 3_000L, -6_000L, -2_000L, -2_000L),
            settlement.balances.map { it.netMinor },
        )
        assertEquals(5, settlement.transfers.size, "the plan: ${settlement.transfers}")
        assertEquals(4, minimumTransfers(settlement.balances.map { it.netMinor }))
    }

    @Test
    fun `how often the plan is the true minimum - informational`() {
        val searched = mutableMapOf<String, Int>()
        val matched = mutableMapOf<String, Int>()
        sweep { trip, settlement, nets ->
            val kind = trip.substringBefore(' ')
            searched.merge(kind, 1, Int::plus)
            if (settlement.transfers.size == minimumTransfers(nets)) matched.merge(kind, 1, Int::plus)
        }
        searched.forEach { (kind, count) ->
            println("Fewest transfers matched the true minimum on ${matched[kind] ?: 0} of $count $kind trips")
        }
    }

    /**
     * Every trip of both sweeps small enough to search, with its settlement and nets. Larger ones are
     * skipped — [randomTrip] never has more than eight members, so in practice none are.
     */
    private fun sweep(check: (trip: String, settlement: Settlement, nets: List<Long>) -> Unit) {
        val trips = seeds.map { "random seed $it" to randomTrip(Random(it)) } +
            seeds.map { "round seed $it" to roundAmountTrip(Random(it)) }
        trips.forEach { (label, trip) ->
            val settlement = settle(trip)
            val nets = settlement.balances.map { it.netMinor }
            if (nets.count { it != 0L } <= MAX_PARTIES) check(label, settlement, nets)
        }
    }

    /** Whole $10 to $40 bills, each fronted by one person for one other — nets that fall into groups. */
    private fun roundAmountTrip(random: Random): Trip {
        val members = (1..random.nextInt(2, 9)).map { MemberId("member-$it") }
        val items = (1..random.nextInt(1, 9)).map { index ->
            val payer = members.random(random)
            Item(ItemId(index.toLong()), random.nextLong(1, 5) * 1_000, payer, listOf((members - payer).random(random)))
        }
        return Trip(members = members, items = items)
    }

    private companion object {
        /** 2^12 subsets is instant; past that the search is the exponential cost it is. */
        const val MAX_PARTIES = 12

        /**
         * The true minimum number of transfers that clears [nets]: the parties not square, less the
         * most disjoint zero-sum groups they split into. `groups[mask]` is the most zero-sum groups
         * any ordering of `mask` passes through — each time a prefix of that ordering sums to zero,
         * one group closes — so `groups[full]` is the best partition of everyone.
         */
        fun minimumTransfers(nets: List<Long>): Int {
            val parties = nets.filter { it != 0L }
            require(parties.size <= MAX_PARTIES) { "too many parties to search: ${parties.size}" }
            val full = (1 shl parties.size) - 1
            val sum = LongArray(full + 1)
            val groups = IntArray(full + 1)
            for (mask in 1..full) {
                sum[mask] = sum[mask and (mask - 1)] + parties[Integer.numberOfTrailingZeros(mask)]
                val best = parties.indices.filter { mask and (1 shl it) != 0 }.maxOf { groups[mask xor (1 shl it)] }
                groups[mask] = best + if (sum[mask] == 0L) 1 else 0
            }
            return parties.size - groups[full]
        }

        /** [nets] less every exact (+a, −a) pair — as many per amount as the plan's exact pass takes. */
        fun withoutExactPairs(nets: List<Long>): List<Long> {
            val left = nets.filter { it != 0L }.toMutableList()
            nets.filter { it < 0L }.forEach { debt ->
                if (debt in left && -debt in left) {
                    left.remove(debt)
                    left.remove(-debt)
                }
            }
            return left
        }

        /** Whether some non-empty subset of [parties], smaller than all of them, sums to zero. */
        fun hasSmallerZeroSumGroup(parties: List<Long>): Boolean {
            val full = (1 shl parties.size) - 1
            return (1 until full).any { mask ->
                parties.indices.filter { mask and (1 shl it) != 0 }.sumOf { parties[it] } == 0L
            }
        }
    }
}
