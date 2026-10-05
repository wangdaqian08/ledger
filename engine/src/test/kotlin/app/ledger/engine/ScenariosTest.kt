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

    // ---- an item that cost nothing ------------------------------------------------------

    @Test
    fun `a zero-amount item settles cleanly alongside a real one`() {
        // A comped round, or a line entered before its price is known: amount zero is a real input,
        // not an error. It must divide into zeros without a special case, read as square on the
        // spot, and leave the trip's balances summing to zero exactly as any other item does.
        val a = m("a")
        val b = m("b")
        val freebie = Item(id = ItemId(1), amountMinor = 0, payer = a, sharedBy = listOf(a, b))
        val dinner = Item(id = ItemId(2), amountMinor = 6_000, payer = a, sharedBy = listOf(a, b))
        val trip = Trip(members = listOf(a, b), items = listOf(freebie, dinner))

        val settlement = settle(trip)

        assertEquals(0L, settlement.balances.sumOf { it.netMinor }, "balances must still sum to zero")
        assertEquals(0L, freebie.shares().values.sum(), "a zero item divides into zeros")
        assertEquals(ItemState.ALL_SQUARE, trip.itemState(freebie.id), "nothing is owed on it, so it is square")
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
                // item.shares() — the real derivation the app reads — not a parallel splitEqually
                // that would ignore the item's actual rule now that trips carry weighted and exact
                // splits too.
                assertEquals(
                    item.amountMinor,
                    item.shares().values.sum(),
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

    @Test
    fun `S6 - suggested transfers never outnumber the people they settle, less one`() {
        // Every transfer zeroes at least one person, and the last zeroes two, so n people who are
        // not square need at most n - 1. Anybody already square needs none and must not be counted.
        (1..1_000).forEach { seed ->
            val settlement = settle(randomTrip(Random(seed)))
            val unsquare = settlement.balances.count { it.netMinor != 0L }

            assertTrue(
                settlement.transfers.size <= maxOf(unsquare - 1, 0),
                "seed $seed: ${settlement.transfers.size} transfers for $unsquare people who are not square",
            )
        }
    }

    @Test
    fun `S6 - money only moves from someone who owes to someone who is owed, never through anyone`() {
        // A transfer list that routed money through a creditor — Eve pays Cat, Cat pays Ann — would
        // ask Cat to send money she is owed. Each person is a sender or a receiver, never both.
        (1..1_000).forEach { seed ->
            val settlement = settle(randomTrip(Random(seed)))
            val senders = settlement.transfers.map { it.from }.toSet()
            val receivers = settlement.transfers.map { it.to }.toSet()

            settlement.transfers.forEach {
                assertTrue(it.amountMinor > 0, "seed $seed: a transfer of zero or less")
                assertTrue(settlement.net(it.from) < 0, "seed $seed: ${it.from.value} sends but owes nothing")
                assertTrue(settlement.net(it.to) > 0, "seed $seed: ${it.to.value} receives but is owed nothing")
            }
            assertEquals(emptySet(), senders intersect receivers, "seed $seed: somebody both sends and receives")
        }
    }

    // ---- S8 · weekend away: fewest transfers ------------------------------------------

    private val ann = m("ann")
    private val ben = m("ben")
    private val cat = m("cat")
    private val dan = m("dan")
    private val eve = m("eve")

    /**
     * UC-1 from the spec. Every split is even with nothing left over, so no salt decides a cent and
     * the transfer set is the same under every roster order — only its order depends on the roster.
     */
    private fun weekendAway(paybacks: List<Payback> = emptyList()) = Trip(
        members = listOf(ann, ben, cat, dan, eve),
        items = listOf(
            Item(ItemId(1), 6_000, payer = ann, sharedBy = listOf(ann, ben, dan)),             // breakfast, $20 each
            Item(ItemId(2), 6_000, payer = ben, sharedBy = listOf(ben, cat, dan, eve)),        // taxi, $15 each
            Item(ItemId(3), 6_000, payer = cat, sharedBy = listOf(ann, cat, dan, eve)),        // lunch, $15 each
        ),
        paybacks = paybacks,
    )

    /** What fewest transfers says, paid as trip-level settlements from the Settle-up screen. */
    private fun theThreeTransfers(status: PaybackStatus) = listOf(
        Payback(eve, cat, 3_000, status),
        Payback(dan, ann, 2_500, status),
        Payback(dan, ben, 2_500, status),
    )

    @Test
    fun `S8 - weekend away - fewest transfers is 3 where greedy took 4`() {
        val trip = weekendAway()
        val settlement = settle(trip)

        assertEquals(2_500L, settlement.net(ann))
        assertEquals(2_500L, settlement.net(ben))
        assertEquals(3_000L, settlement.net(cat))
        assertEquals(-5_000L, settlement.net(dan))
        assertEquals(-3_000L, settlement.net(eve))

        // Plain largest-against-largest greedy pairs Dan with Cat first and needs four:
        // Dan→Cat 30, Dan→Ann 20, Eve→Ann 5, Eve→Ben 25. Matching Eve's $30 to Cat's $30 first
        // settles both in one move and leaves a problem greedy can finish in two.
        assertEquals(
            listOf(
                Transfer(eve, cat, 3_000),                     // the exact pair
                Transfer(dan, ann, 2_500),                     // greedy, ann before ben by roster
                Transfer(dan, ben, 2_500),
            ),
            settlement.transfers,
        )

        // By person tells the same story in eight rows.
        assertEquals(2_000L, owesBetween(trip, ben, ann))
        assertEquals(1_500L, owesBetween(trip, ann, cat))
        assertEquals(2_000L, owesBetween(trip, dan, ann))
        assertEquals(1_500L, owesBetween(trip, cat, ben))
        assertEquals(1_500L, owesBetween(trip, dan, ben))
        assertEquals(1_500L, owesBetween(trip, eve, ben))
        assertEquals(1_500L, owesBetween(trip, dan, cat))
        assertEquals(1_500L, owesBetween(trip, eve, cat))
        assertEquals(0L, owesBetween(trip, ann, eve))
        assertEquals(0L, owesBetween(trip, dan, eve))
    }

    @Test
    fun `S8 - paying the three transfers squares everyone while the rows still cancel`() {
        val trip = weekendAway(paybacks = theThreeTransfers(PaybackStatus.APPROVED))
        val settlement = settle(trip)

        assertEquals(emptyList(), settlement.transfers)
        trip.members.forEach { assertEquals(0L, settlement.net(it), "${it.value} should be square overall") }

        // Square overall is not every row at zero. Eve paid Cat for the taxi share she owed Ben,
        // so Eve still owes Ben $15 and Cat owes Eve $15 — truthful history that nets to nothing.
        assertEquals(1_500L, owesBetween(trip, eve, ben))
        assertEquals(-1_500L, owesBetween(trip, eve, cat))
        assertEquals(-500L, owesBetween(trip, dan, ann))
        assertEquals(-1_000L, owesBetween(trip, dan, ben))
        assertEquals(1_500L, owesBetween(trip, dan, cat))

        // Invariant 2 does not care: every person's rows still sum to −net, which is now 0.
        trip.members.forEach { person ->
            val rows = trip.members.filter { it != person }.sumOf { owesBetween(trip, person, it) }
            assertEquals(-settlement.net(person), rows, "${person.value}'s rows no longer sum to −net")
        }
    }

    @Test
    fun `S8 - the three transfers claimed but not yet approved move nothing`() {
        val before = settle(weekendAway())
        val pending = settle(weekendAway(paybacks = theThreeTransfers(PaybackStatus.PENDING)))

        assertEquals(before.transfers, pending.transfers)
        assertEquals(before.balances, pending.balances)
    }

    @Test
    fun `S8 - weekend away - how it adds up, before anybody pays`() {
        // The table a person checks the maths against. On every row paid − share + settled is the
        // balance; $180 fronted is $180 of shares; balances sum to zero. By person needs eight pairs
        // where the plan needs three lines — Dan is on two of them, everybody else on one.
        val breakdown = breakdown(weekendAway())

        assertEquals(
            listOf(
                //           member  fronted    share   settled       net   by person   plan
                MemberBreakdown(ann,   6_000,   3_500,        0,    2_500,          3,     1),
                MemberBreakdown(ben,   6_000,   3_500,        0,    2_500,          4,     1),
                MemberBreakdown(cat,   6_000,   3_000,        0,    3_000,          4,     1),
                MemberBreakdown(dan,       0,   5_000,        0,   -5_000,          3,     2),
                MemberBreakdown(eve,       0,   3_000,        0,   -3_000,          2,     1),
            ),
            breakdown.rows,
        )
        assertEquals(18_000L, breakdown.frontedMinor)               // three $60 bills
        assertEquals(18_000L, breakdown.shareMinor)                 // every cent of them shared out
        assertEquals(0L, breakdown.settledMinor)
        assertEquals(0L, breakdown.netMinor)
        assertEquals(8, breakdown.bilateralPairs)                   // pairs, not 16 row-ends
        assertEquals(3, breakdown.planTransfers)
    }

    @Test
    fun `S8 - weekend away - how it adds up once the three transfers are paid`() {
        // The settled column is what took every balance to zero: Eve and Dan sent theirs, Cat, Ann
        // and Ben received theirs, and the column sums to zero. Nothing is left on the plan — yet By
        // person still has eight non-zero pairs, because the rows cancel rather than vanish.
        val breakdown = breakdown(weekendAway(paybacks = theThreeTransfers(PaybackStatus.APPROVED)))

        assertEquals(
            listOf(
                //           member  fronted    share   settled       net   by person   plan
                MemberBreakdown(ann,   6_000,   3_500,   -2_500,        0,          3,     0),
                MemberBreakdown(ben,   6_000,   3_500,   -2_500,        0,          4,     0),
                MemberBreakdown(cat,   6_000,   3_000,   -3_000,        0,          4,     0),
                MemberBreakdown(dan,       0,   5_000,    5_000,        0,          3,     0),
                MemberBreakdown(eve,       0,   3_000,    3_000,        0,          2,     0),
            ),
            breakdown.rows,
        )
        assertEquals(18_000L, breakdown.frontedMinor)
        assertEquals(18_000L, breakdown.shareMinor)
        assertEquals(0L, breakdown.settledMinor)                    // every cent sent was received
        assertEquals(0L, breakdown.netMinor)
        assertEquals(8, breakdown.bilateralPairs)                   // cancelling, not zero
        assertEquals(0, breakdown.planTransfers)
    }

    @Test
    fun `S8 - weekend away - claimed but not approved, the table does not move`() {
        assertEquals(
            breakdown(weekendAway()),
            breakdown(weekendAway(paybacks = theThreeTransfers(PaybackStatus.PENDING))),
        )
    }

    // ---- S9 · families settle as one party ---------------------------------------------

    @Test
    fun `S9 - families settle as one party`() {
        // AC-07 from the PRD: Ann pays $400 shared by Ann, Ben, Cat and Dan, so the other three owe
        // her $100 each. Ben and Cat are one Family, so they pay her once, together — whatever they
        // owe each other is theirs to sort out, which is the point of building a Family at all.
        val trip = Trip(
            members = listOf(ann, ben, cat, dan),
            items = listOf(Item(ItemId(1), 40_000, payer = ann, sharedBy = listOf(ann, ben, cat, dan))),
        )

        assertEquals(
            listOf(
                FamilyTransfer(Family(setOf(ben, cat)), Family(setOf(ann)), 20_000),
                FamilyTransfer(Family(setOf(dan)), Family(setOf(ann)), 10_000),
            ),
            familyTransfers(partitionIntoFamilies(trip, listOf(setOf(ben, cat)))),
        )
    }

    @Test
    fun `S9 - weekend away - Ann and Ben as one family need two transfers where people need three`() {
        // UC-2. Ann and Ben are owed $25 each, so as one Family they are owed $50 — exactly what
        // Dan owes. Both lines are exact pairs, and the third transfer of S8 simply disappears.
        val families = partitionIntoFamilies(weekendAway(), listOf(setOf(ann, ben)))

        assertEquals(
            listOf(
                FamilyTransfer(Family(setOf(dan)), Family(setOf(ann, ben)), 5_000),
                FamilyTransfer(Family(setOf(eve)), Family(setOf(cat)), 3_000),
            ),
            familyTransfers(families),
        )
    }

    @Test
    fun `S9 - weekend away - Dan and Eve as one family pay each creditor once`() {
        // Dan and Eve owe $80 between them; nobody is owed exactly that, so largest against largest:
        // Cat's $30 first, then Ann before Ben — tied at $25, broken on partition order.
        val families = partitionIntoFamilies(weekendAway(), listOf(setOf(dan, eve)))

        assertEquals(
            listOf(
                FamilyTransfer(Family(setOf(dan, eve)), Family(setOf(cat)), 3_000),
                FamilyTransfer(Family(setOf(dan, eve)), Family(setOf(ann)), 2_500),
                FamilyTransfer(Family(setOf(dan, eve)), Family(setOf(ben)), 2_500),
            ),
            familyTransfers(families),
        )
    }

}
