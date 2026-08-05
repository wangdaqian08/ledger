package app.ledger.engine

/** Identifies one person within a trip. */
@JvmInline
value class MemberId(val value: String)

/**
 * Divides [totalMinor] equally between [members], in minor currency units.
 *
 * The parts always sum to exactly [totalMinor] — never a cent more or less.
 */
fun splitEqually(totalMinor: Long, members: List<MemberId>, salt: Long = 0): Map<MemberId, Long> =
    shares(totalMinor, members, SplitRule.Equal, salt)

/** How an item's total is divided between the people on it. */
sealed interface SplitRule {
    /** Everyone on the item pays the same. */
    data object Equal : SplitRule

    /** Proportional shares — what the demo's draggable SplitBar produces. */
    data class Weighted(val weights: Map<MemberId, Int>) : SplitRule

    /** Amounts typed against each name; they must add up to the item total. */
    data class Exact(val amounts: Map<MemberId, Long>) : SplitRule
}

/**
 * Divides [totalMinor] between [members] according to [rule].
 *
 * Whichever rule is used, the parts sum to exactly [totalMinor].
 */
fun shares(
    totalMinor: Long,
    members: List<MemberId>,
    rule: SplitRule,
    salt: Long = 0,
): Map<MemberId, Long> {
    require(members.isNotEmpty()) { "cannot split $totalMinor between no one" }
    // Refunds (§9) are a decision not yet taken; until they are, a negative total must be refused
    // here rather than quietly mis-split — Kotlin division truncates toward zero, so the largest
    // remainder arithmetic below is only correct for totals that are not negative.
    require(totalMinor >= 0) { "cannot split a negative amount, got $totalMinor" }
    require(members.size == members.toSet().size) { "the same person cannot be on one item twice" }

    return when (rule) {
        SplitRule.Equal -> {
            splitByWeight(totalMinor, members, members.associateWith { 1 }, salt)
        }

        is SplitRule.Weighted -> {
            val weights = members.associateWith { member ->
                val weight = rule.weights[member] ?: 0
                require(weight >= 0) { "${member.value} has a negative weight of $weight" }
                weight
            }
            require(weights.values.any { it > 0 }) { "at least one person must carry some weight" }
            splitByWeight(totalMinor, members, weights, salt)
        }

        is SplitRule.Exact -> {
            require(rule.amounts.keys == members.toSet()) {
                "exact amounts must name exactly the people on the item"
            }
            // Checked per person, not only in sum: a negative here and padding there cancel out.
            rule.amounts.forEach { (member, amount) ->
                require(amount >= 0) { "${member.value} has a negative amount of $amount" }
            }
            val given = rule.amounts.values.sum()
            require(given == totalMinor) {
                "exact amounts add up to $given but the item total is $totalMinor"
            }
            members.associateWith { rule.amounts.getValue(it) }
        }
    }
}

private class Part(val member: MemberId, val base: Long, val fraction: Long, val rotated: Int)

/**
 * Largest remainder: floor every share, then hand the leftover cents to the largest
 * fractional parts.
 *
 * Ties break on position rotated by [salt], so across a trip's items the spare cent lands
 * on different people rather than always the first name on the list. With every weight at
 * 1 every fraction ties, which makes this reduce exactly to [splitEqually].
 */
private fun splitByWeight(
    totalMinor: Long,
    members: List<MemberId>,
    weights: Map<MemberId, Int>,
    salt: Long,
): Map<MemberId, Long> {
    val totalWeight = weights.values.sumOf { it.toLong() }
    val count = members.size
    val offset = (salt % count).toInt()

    // Refusing beats silently wrapping: past this, totalMinor * weight no longer fits in a Long
    // and every number that falls out of the wreckage still looks plausible. The web port draws
    // the same line at its own limit (Number.MAX_SAFE_INTEGER).
    val heaviest = weights.values.max()
    require(heaviest == 0 || totalMinor <= Long.MAX_VALUE / heaviest) {
        "the amount and weights are too large to split exactly"
    }

    val parts = members.mapIndexed { index, member ->
        val numerator = totalMinor * weights.getValue(member)
        Part(
            member = member,
            base = numerator / totalWeight,
            fraction = numerator % totalWeight,
            rotated = ((index - offset) % count + count) % count,
        )
    }

    // Leftover is always smaller than the number of people carrying a non-zero fraction,
    // so someone on a zero weight can never be handed a stray cent.
    val leftover = (totalMinor - parts.sumOf { it.base }).toInt()
    val getsSpareCent = parts
        .sortedWith(compareByDescending<Part> { it.fraction }.thenBy { it.rotated })
        .take(leftover)
        .mapTo(mutableSetOf()) { it.member }

    return parts.associate { it.member to it.base + if (it.member in getsSpareCent) 1L else 0L }
}
