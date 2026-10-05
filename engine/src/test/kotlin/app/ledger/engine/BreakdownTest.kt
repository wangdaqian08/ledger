package app.ledger.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * "How it adds up" on the Settle-up screen — [breakdown]. It exists so a person can check the maths
 * by hand, so these are the identities a person would check, proven over a thousand random trips:
 * every row ties out to the settlement's own net, the columns total to what the trip cost and to
 * zero, and the two transfer counts agree with By person and with the plan. S8 pins exact figures.
 */
class BreakdownTest {
    private val seeds = 1..1_000

    @Test
    fun `every row ties out - fronted less share plus settled is the settlement's own net`() {
        seeds.forEach { seed ->
            val trip = randomTrip(Random(seed))
            val settlement = settle(trip)
            val breakdown = breakdown(trip)

            assertEquals(trip.members, breakdown.rows.map { it.member }, "seed $seed: one row per member, roster order")
            breakdown.rows.forEach { row ->
                val who = "seed $seed, ${row.member.value}"
                assertEquals(settlement.net(row.member), row.netMinor, "$who: not the settlement's net")
                assertEquals(
                    row.netMinor,
                    row.frontedMinor - row.shareMinor + row.settledMinor,
                    "$who: row does not tie out",
                )
            }
        }
    }

    @Test
    fun `settled is approved money only, sent less received, whether item-linked or trip-level`() {
        seeds.forEach { seed ->
            val trip = randomTrip(Random(seed))
            val approved = trip.paybacks.filter { it.status == PaybackStatus.APPROVED }

            breakdown(trip).rows.forEach { row ->
                val sent = approved.filter { it.from == row.member }.sumOf { it.amountMinor }
                val received = approved.filter { it.to == row.member }.sumOf { it.amountMinor }
                assertEquals(sent - received, row.settledMinor, "seed $seed, ${row.member.value}")
            }
        }
    }

    @Test
    fun `the totals tie out - fronted equals shares equals what the items cost, settled and net sum to zero`() {
        seeds.forEach { seed ->
            val trip = randomTrip(Random(seed))
            val breakdown = breakdown(trip)
            val spend = trip.items.sumOf { it.amountMinor }

            assertEquals(spend, breakdown.frontedMinor, "seed $seed: fronted total is not the group spend")
            assertEquals(spend, breakdown.shareMinor, "seed $seed: shares total is not the group spend")
            assertEquals(0L, breakdown.settledMinor, "seed $seed: settled total is not zero")
            assertEquals(0L, breakdown.netMinor, "seed $seed: net total is not zero")

            // The totals are the rows' own sums — the client renders them and never adds anything up.
            assertEquals(breakdown.rows.sumOf { it.frontedMinor }, breakdown.frontedMinor, "seed $seed")
            assertEquals(breakdown.rows.sumOf { it.shareMinor }, breakdown.shareMinor, "seed $seed")
            assertEquals(breakdown.rows.sumOf { it.settledMinor }, breakdown.settledMinor, "seed $seed")
            assertEquals(breakdown.rows.sumOf { it.netMinor }, breakdown.netMinor, "seed $seed")
        }
    }

    @Test
    fun `the by person count is the non-zero rows, and each pair is counted from both ends`() {
        seeds.forEach { seed ->
            val trip = randomTrip(Random(seed))
            val breakdown = breakdown(trip)

            breakdown.rows.forEach { row ->
                val nonZero = trip.members.count { it != row.member && owesBetween(trip, row.member, it) != 0L }
                assertEquals(nonZero, row.bilateralCount, "seed $seed, ${row.member.value}")
            }
            assertEquals(2 * breakdown.bilateralPairs, breakdown.rows.sumOf { it.bilateralCount }, "seed $seed")
        }
    }

    @Test
    fun `the plan count is the lines each person is on, and each line has two ends`() {
        seeds.forEach { seed ->
            val trip = randomTrip(Random(seed))
            val settlement = settle(trip)
            val breakdown = breakdown(trip)

            assertEquals(settlement.transfers.size, breakdown.planTransfers, "seed $seed")
            breakdown.rows.forEach { row ->
                val lines = settlement.transfers.count { it.from == row.member || it.to == row.member }
                assertEquals(lines, row.planCount, "seed $seed, ${row.member.value}")
            }
            assertEquals(2 * breakdown.planTransfers, breakdown.rows.sumOf { it.planCount }, "seed $seed")
        }
    }
}
