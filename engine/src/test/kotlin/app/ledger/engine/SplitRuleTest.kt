package app.ledger.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The demo's add-expense sheet offers Evenly or Custom, where Custom is a draggable
 * SplitBar producing a weight per person. Typed per-person amounts are a third case.
 *
 * All three must land on parts that sum to the item total exactly.
 */
class SplitRuleTest {
    private val amy = MemberId("amy")
    private val bob = MemberId("bob")
    private val cara = MemberId("cara")
    private val everyone = listOf(amy, bob, cara)

    // ---- Equal ---------------------------------------------------------------------

    @Test
    fun `equal is the same thing splitEqually always did`() {
        assertEquals(
            splitEqually(10_000, everyone, salt = 2),
            shares(10_000, everyone, SplitRule.Equal, salt = 2),
        )
    }

    // ---- Weighted, which is what the SplitBar drag produces --------------------------

    @Test
    fun `weighted splits in proportion`() {
        val result = shares(12_000, everyone, SplitRule.Weighted(mapOf(amy to 2, bob to 1, cara to 1)))

        assertEquals(mapOf(amy to 6_000L, bob to 3_000L, cara to 3_000L), result)
    }

    @Test
    fun `weighted still sums to the total exactly when the proportions do not divide`() {
        // 40 / 20 / 40 of $120.50 is 48.20 / 24.10 / 48.20 — but most drags aren't that tidy.
        val result = shares(10_000, everyone, SplitRule.Weighted(mapOf(amy to 1, bob to 1, cara to 1)))

        assertEquals(10_000L, result.values.sum())
    }

    @Test
    fun `weighted sums exactly across many awkward weightings`() {
        (1..30).forEach { a ->
            (1..30).forEach { b ->
                val rule = SplitRule.Weighted(mapOf(amy to a, bob to b, cara to 7))
                val result = shares(123_457, everyone, rule, salt = a.toLong())

                assertEquals(123_457L, result.values.sum(), "weights $a/$b/7")
                assertTrue(result.values.all { it >= 0 }, "weights $a/$b/7 produced a negative share")
            }
        }
    }

    @Test
    fun `a zero weight means that person owes nothing`() {
        val result = shares(10_000, everyone, SplitRule.Weighted(mapOf(amy to 1, bob to 0, cara to 1)))

        assertEquals(0L, result[bob])
        assertEquals(10_000L, result.values.sum())
    }

    @Test
    fun `refuses a weighting where everybody is zero`() {
        assertFailsWith<IllegalArgumentException> {
            shares(10_000, everyone, SplitRule.Weighted(mapOf(amy to 0, bob to 0, cara to 0)))
        }
    }

    // ---- Exact, for typed per-person amounts -----------------------------------------

    @Test
    fun `exact amounts are used as given`() {
        val rule = SplitRule.Exact(mapOf(amy to 5_000L, bob to 3_000L, cara to 2_000L))

        assertEquals(mapOf(amy to 5_000L, bob to 3_000L, cara to 2_000L), shares(10_000, everyone, rule))
    }

    @Test
    fun `exact amounts that miss the total are rejected rather than quietly fudged`() {
        // Silently normalising a typo would produce a wrong bill that nobody can spot.
        val rule = SplitRule.Exact(mapOf(amy to 5_000L, bob to 3_000L, cara to 1_999L))

        val thrown = assertFailsWith<IllegalArgumentException> { shares(10_000, everyone, rule) }
        assertTrue(
            thrown.message!!.contains("9999") && thrown.message!!.contains("10000"),
            "message should name both totals, was: ${thrown.message}",
        )
    }

    @Test
    fun `exact amounts must cover exactly the people on the item`() {
        val missingCara = SplitRule.Exact(mapOf(amy to 5_000L, bob to 5_000L))

        assertFailsWith<IllegalArgumentException> { shares(10_000, everyone, missingCara) }
    }

    // ---- the rule has to survive the trip round-trip, not just the split function -----

    /** Amy drags her handle to double: she takes half, Bob and Cara a quarter each. */
    private fun draggedItem() = Item(
        id = ItemId(1),
        amountMinor = 12_000,
        payer = amy,
        sharedBy = everyone,
        split = SplitRule.Weighted(mapOf(amy to 2, bob to 1, cara to 1)),
    )

    @Test
    fun `a weighted item bills each person their dragged portion`() {
        val settlement = settle(Trip(members = everyone, items = listOf(draggedItem())))

        // Amy fronted $120 and owes $60 of it, so she is owed $60 back — $30 from each.
        assertEquals(6_000L, settlement.net(amy))
        assertEquals(-3_000L, settlement.net(bob))
        assertEquals(-3_000L, settlement.net(cara))
        assertEquals(0L, settlement.balances.sumOf { it.netMinor })
    }

    @Test
    fun `a weighted item only goes square when the dragged portions are covered`() {
        val item = draggedItem()

        fun tripWith(vararg paybacks: Payback) =
            Trip(members = everyone, items = listOf(item), paybacks = paybacks.toList())

        // An equal-split-sized payback is not enough: Bob's dragged portion is $30, not $40.
        val underPaid = tripWith(item.repaidBy(bob, 3_000), item.repaidBy(cara, 2_000))
        assertEquals(ItemState.OPEN, underPaid.itemState(item.id))

        val covered = tripWith(item.repaidBy(bob, 3_000), item.repaidBy(cara, 3_000))
        assertEquals(ItemState.ALL_SQUARE, covered.itemState(item.id))
    }

    @Test
    fun `an exact-split item bills the typed amounts`() {
        val item = Item(
            id = ItemId(2),
            amountMinor = 10_000,
            payer = amy,
            sharedBy = everyone,
            split = SplitRule.Exact(mapOf(amy to 2_000L, bob to 3_000L, cara to 5_000L)),
        )

        val settlement = settle(Trip(members = everyone, items = listOf(item)))

        assertEquals(8_000L, settlement.net(amy))
        assertEquals(-3_000L, settlement.net(bob))
        assertEquals(-5_000L, settlement.net(cara))
    }
}
