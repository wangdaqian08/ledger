package app.ledger.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S1 from docs/specs/2026-07-31-ledger-design.md — the case that justifies the app.
 *
 * Bob fronts a $1,000 hotel deposit when 10 people are coming, then the $1,000 balance
 * when 13 are coming. On arrival there are 14: Jack turned up at check-in and has paid
 * nothing. The hotel bill never changed, but everyone's fair share did, three times.
 *
 * Correcting the two items' people lists to all 14 is the entire fix. The ten who already
 * paid must come out AHEAD — they are owed money back — and Jack must owe a full share.
 */
class HotelScenarioTest {

    private val bob = MemberId("bob")
    private val originals = (1..9).map { MemberId("original-$it") }   // paid at both stages
    private val newcomers = (1..3).map { MemberId("newcomer-$it") }   // paid at stage 2 only
    private val jack = MemberId("jack")                              // paid nothing

    private val everyone = listOf(bob) + originals + newcomers + listOf(jack)

    private val trip = Trip(
        members = everyone,
        items = listOf(
            Item(
                id = ItemId(1),
                amountMinor = 100_000,                    // $1,000 deposit
                payer = bob,
                sharedBy = everyone,                      // corrected from 10 to all 14
                // The 9 originals each handed Bob $100 the day after booking.
                paybacks = originals.map { Payback(it, 10_000, PaybackStatus.APPROVED) },
            ),
            Item(
                id = ItemId(2),
                amountMinor = 100_000,                    // $1,000 balance
                payer = bob,
                sharedBy = everyone,                      // corrected from 13 to all 14
                // On arrival, the 12 others in the group of 13 each sent Bob $1,000/13.
                paybacks = (originals + newcomers).map {
                    Payback(it, 7_692, PaybackStatus.APPROVED)
                },
            ),
        ),
    )

    private val settlement = settle(trip)

    @Test
    fun `everyone who already paid comes out ahead once Jack is added`() {
        // This is the point of the whole product. Adding a 14th person to a bill that was
        // already settled for 13 must push money BACK to the people who overpaid.
        assertTrue(settlement.net(bob) > 0, "Bob fronted the lot and should be owed")
        originals.forEach {
            assertTrue(settlement.net(it) > 0, "$it paid at both stages and should be owed")
        }
    }

    @Test
    fun `the three who joined late still owe, and Jack owes a full share`() {
        newcomers.forEach {
            assertTrue(settlement.net(it) < 0, "$it only paid a thirteenth and still owes")
        }

        // Jack paid nothing, so he owes exactly his share of both items and not a cent more.
        val jacksShare = trip.items.sumOf { splitEqually(it.amountMinor, it.sharedBy, it.id.value)[jack]!! }
        assertEquals(-jacksShare, settlement.net(jack))
    }

    @Test
    fun `the column sums to exactly zero`() {
        assertEquals(0L, settlement.balances.sumOf { it.netMinor })
    }

    @Test
    fun `lands on the figures published in the spec`() {
        // Quoted in dollars in the spec; asserted here to the cent. The odd cent lands on
        // different members for each item because the split salt rotates, which is why
        // Bob and original-1 sit a cent or two off the other eight.
        assertEquals(3_412L, settlement.net(bob))                      // +$34.12
        assertEquals(3_407L, settlement.net(originals[0]))             // +$34.07
        originals.drop(1).forEach {
            assertEquals(3_406L, settlement.net(it))                   // +$34.06
        }
        newcomers.forEach {
            assertEquals(-6_594L, settlement.net(it))                  // -$65.94
        }
        assertEquals(-14_285L, settlement.net(jack))                   // -$142.85
    }

    @Test
    fun `every item's shares add up to that item's exact total`() {
        trip.items.forEach { item ->
            val shares = splitEqually(item.amountMinor, item.sharedBy, item.id.value)
            assertEquals(item.amountMinor, shares.values.sum(), "item ${item.id.value}")
        }
    }
}
