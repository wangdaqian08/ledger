package app.ledger.engine

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Families on the Settle-up screen — see §7b of the spec.
 *
 * [partitionIntoFamilies] turns whatever explicit Families the viewer has built so far into a
 * complete partition of the trip, then gives each Family its own net plus its bilateral position
 * with every *other* Family — never with an individual, and never [settle]'s minimised transfer
 * set. `betweenFamilies` is the direct Family-level analogue of [owesBetween]'s bilateral rows.
 */
class FamilyTest {
    private val alice = MemberId("alice")
    private val bob = MemberId("bob")
    private val cathy = MemberId("cathy")
    private val dana = MemberId("dana")
    private val erin = MemberId("erin")
    private val everyone = listOf(alice, bob, cathy, dana, erin)

    /** The spec's worked example: A–E, enough cross-pair debt to tell families apart. */
    private fun worked(): Trip {
        val dinner = Item(ItemId(1), 50_000, payer = alice, sharedBy = everyone)
        val taxi = Item(ItemId(2), 6_000, payer = bob, sharedBy = listOf(bob, cathy))
        val groceries = Item(ItemId(3), 4_000, payer = dana, sharedBy = listOf(dana, erin))
        return Trip(members = everyone, items = listOf(dinner, taxi, groceries))
    }

    // ---- building the partition --------------------------------------------------------

    @Test
    fun `everyone left out of an explicit family becomes their own one-person family`() {
        val families = partitionIntoFamilies(worked(), listOf(setOf(alice, bob)))

        assertEquals(
            setOf(setOf(alice, bob), setOf(cathy), setOf(dana), setOf(erin)),
            families.map { it.family.members }.toSet(),
        )
    }

    @Test
    fun `with nothing built explicitly, everyone is their own family`() {
        val families = partitionIntoFamilies(worked(), emptyList())

        assertEquals(everyone.map { setOf(it) }, families.map { it.family.members })
    }

    @Test
    fun `an explicit family built from the last two names in roster order still comes first`() {
        // dana and erin are the last two names in roster order, so a partition that fell back to
        // roster order instead of build order would put this family last, not first. Asserting on
        // the literal list — index 0, never .toSet() — is what tells the two apart.
        val families = partitionIntoFamilies(worked(), listOf(setOf(dana, erin)))

        assertEquals(setOf(dana, erin), families[0].family.members)
        assertEquals(
            listOf(setOf(dana, erin), setOf(alice), setOf(bob), setOf(cathy)),
            families.map { it.family.members },
        )
    }

    @Test
    fun `two families can cover everyone between them, with nothing left over`() {
        val families = partitionIntoFamilies(worked(), listOf(setOf(alice, bob), setOf(cathy, dana, erin)))

        assertEquals(2, families.size)
        assertEquals(
            setOf(setOf(alice, bob), setOf(cathy, dana, erin)),
            families.map { it.family.members }.toSet(),
        )
    }

    @Test
    fun `a family's counterparts are the other families, not the other individuals`() {
        // {alice,bob}, {cathy,dana}, {erin} — 3 families, so 2 counterparts each. Never
        // 5 - 2 = 3, which is what per-individual counterparts would give {alice,bob}.
        val families = partitionIntoFamilies(worked(), listOf(setOf(alice, bob), setOf(cathy, dana)))

        assertEquals(3, families.size)
        families.forEach { balance ->
            assertEquals(2, balance.betweenFamilies.size, "${balance.family.members} counterpart count")
        }
    }

    @Test
    fun `a family-vs-family amount is the raw sum of every cross pair, never a minimised one`() {
        // Bob is the trap: alice owes him for dinner and he owes cathy for the taxi, so his own
        // net is zero and settle()'s suggestTransfers connects alice straight to cathy, skipping
        // bob entirely. The raw bilateral figure between {alice} and {bob} must still be $10 —
        // it can never be read off that minimised list.
        val dinner = Item(ItemId(1), 2_000, payer = bob, sharedBy = listOf(alice, bob))
        val taxi = Item(ItemId(2), 2_000, payer = cathy, sharedBy = listOf(bob, cathy))
        val trip = Trip(members = listOf(alice, bob, cathy), items = listOf(dinner, taxi))

        // Confirm the trap is real: the minimised transfer set really does skip bob.
        assertEquals(listOf(Transfer(alice, cathy, 1_000)), settle(trip).transfers)

        val families = partitionIntoFamilies(trip, emptyList())
        val aliceFamily = families.first { it.family.members == setOf(alice) }

        assertEquals(1_000L, aliceFamily.betweenFamilies.getValue(Family(setOf(bob))))
    }

    @Test
    fun `an explicit one-person family is allowed, and is identical to leaving them out`() {
        val explicit = partitionIntoFamilies(worked(), listOf(setOf(alice)))
        val implicit = partitionIntoFamilies(worked(), emptyList())

        assertEquals(implicit, explicit)
    }

    // ---- refusals -------------------------------------------------------------------------

    @Test
    fun `refuses an empty explicit family`() {
        assertFailsWith<IllegalArgumentException> {
            partitionIntoFamilies(worked(), listOf(emptySet()))
        }
    }

    @Test
    fun `refuses two explicit families sharing a member`() {
        assertFailsWith<IllegalArgumentException> {
            partitionIntoFamilies(worked(), listOf(setOf(alice, bob), setOf(bob, cathy)))
        }
    }

    @Test
    fun `refuses a member not on the trip`() {
        assertFailsWith<IllegalArgumentException> {
            partitionIntoFamilies(worked(), listOf(setOf(alice, MemberId("stranger"))))
        }
    }

    @Test
    fun `refuses a single family covering the whole trip`() {
        // It would leave nobody to auto-singleton, producing exactly 1 family, which means nothing.
        assertFailsWith<IllegalArgumentException> {
            partitionIntoFamilies(worked(), listOf(everyone.toSet()))
        }
    }

    // ---- the smallest trip pins the exact boundary -----------------------------------------

    @Test
    fun `the smallest trip cannot be partitioned into one family of both`() {
        val trip = Trip(members = listOf(alice, bob), items = emptyList())

        assertFailsWith<IllegalArgumentException> {
            partitionIntoFamilies(trip, listOf(setOf(alice, bob)))
        }
    }

    @Test
    fun `but zero explicit families on the smallest trip is fine`() {
        val trip = Trip(members = listOf(alice, bob), items = emptyList())

        val families = partitionIntoFamilies(trip, emptyList())

        assertEquals(setOf(setOf(alice), setOf(bob)), families.map { it.family.members }.toSet())
    }

    // ---- properties, 500 random trips and partitions each ----------------------------------

    /**
     * A random, always-valid partial partition: some members grouped into disjoint explicit
     * families, the rest left to auto-singleton. The last shuffled member is always left ungrouped,
     * guaranteeing 2+ families in the result on every seed.
     */
    private fun randomExplicitFamilies(members: List<MemberId>, random: Random): List<Set<MemberId>> {
        val groupable = members.shuffled(random).dropLast(1)
        val families = mutableListOf<Set<MemberId>>()
        var index = 0
        while (index < groupable.size) {
            if (random.nextBoolean()) {
                val size = random.nextInt(1, groupable.size - index + 1)
                families += groupable.subList(index, index + size).toSet()
                index += size
            } else {
                index++
            }
        }
        return families
    }

    @Test
    fun `every family's net summed across the partition is zero`() {
        (1..500).forEach { seed ->
            val random = Random(seed)
            val trip = randomTrip(random)
            val families = partitionIntoFamilies(trip, randomExplicitFamilies(trip.members, random))

            assertEquals(0L, families.sumOf { it.netMinor }, "seed $seed: families' nets don't sum to zero")
        }
    }

    @Test
    fun `each family's counterpart breakdown sums to exactly minus its own net`() {
        (1..500).forEach { seed ->
            val random = Random(seed)
            val trip = randomTrip(random)
            val families = partitionIntoFamilies(trip, randomExplicitFamilies(trip.members, random))

            families.forEach { balance ->
                assertEquals(
                    -balance.netMinor,
                    balance.betweenFamilies.values.sum(),
                    "seed $seed: ${balance.family.members} rows don't add up to its net",
                )
            }
        }
    }

    @Test
    fun `the partition covers every trip member exactly once`() {
        (1..500).forEach { seed ->
            val random = Random(seed)
            val trip = randomTrip(random)
            val families = partitionIntoFamilies(trip, randomExplicitFamilies(trip.members, random))

            val allMembers = families.flatMap { it.family.members }
            assertEquals(trip.members.size, allMembers.size, "seed $seed: duplicate or missing member")
            assertEquals(
                trip.members.toSet(),
                allMembers.toSet(),
                "seed $seed: partition doesn't cover every member",
            )
        }
    }

    @Test
    fun `a family's figure with another is the exact negative of that family's figure back`() {
        (1..500).forEach { seed ->
            val random = Random(seed)
            val trip = randomTrip(random)
            val families = partitionIntoFamilies(trip, randomExplicitFamilies(trip.members, random))

            families.forEach { balance ->
                balance.betweenFamilies.forEach { (other, amount) ->
                    val otherBalance = families.first { it.family == other }
                    assertEquals(
                        -amount,
                        otherBalance.betweenFamilies.getValue(balance.family),
                        "seed $seed: ${balance.family.members} and ${other.members} disagree",
                    )
                }
            }
        }
    }
}
