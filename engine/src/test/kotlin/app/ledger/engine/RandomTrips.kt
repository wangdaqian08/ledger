package app.ledger.engine

import kotlin.random.Random

/**
 * A random but always-valid trip, shared by the property tests.
 *
 * Items span all three split rules — equal, weighted and exact — so the invariants are proven
 * against the ways people actually divide a bill, not only the even case. (They used to be Equal
 * only, which is how a weighted/exact rounding bug could have slipped past the property sweep.)
 * Repayments and trip-level settlements arrive in every status, deliberately over- and under-paid.
 */
fun randomTrip(random: Random): Trip {
    val members = (1..random.nextInt(2, 9)).map { MemberId("member-$it") }

    val items = (1..random.nextInt(0, 7)).map { index ->
        val amount = random.nextLong(1, 500_000)
        // A non-empty people list, sometimes excluding the payer — someone can front a bill they
        // take no part in, like covering everyone else's lift passes.
        val sharedBy = members.filter { random.nextBoolean() }.ifEmpty { listOf(members.first()) }
        Item(
            id = ItemId(index.toLong()),
            amountMinor = amount,
            payer = members.random(random),
            sharedBy = sharedBy,
            split = randomRule(amount, sharedBy, random),
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

private fun randomRule(amount: Long, sharedBy: List<MemberId>, random: Random): SplitRule =
    when (random.nextInt(3)) {
        0 -> {
            SplitRule.Equal
        }

        1 -> {
            val weights = sharedBy.associateWith { random.nextInt(0, 5) }
            // At least one positive weight, or there is nothing to divide by.
            SplitRule.Weighted(if (weights.values.any { it > 0 }) weights else weights + (sharedBy.first() to 1))
        }

        else -> {
            SplitRule.Exact(sharedBy.zip(partition(amount, sharedBy.size, random)).toMap())
        }
    }

/** [n] non-negative parts summing to exactly [total] — a valid Exact split of it. */
private fun partition(total: Long, n: Int, random: Random): List<Long> {
    var remaining = total
    val head = (1 until n).map {
        val part = random.nextLong(0, remaining + 1)
        remaining -= part
        part
    }
    return head + remaining
}
