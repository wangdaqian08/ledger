package app.ledger.engine

/** Identifies one person within a trip. */
@JvmInline
value class MemberId(val value: String)

/**
 * Divides [totalMinor] equally between [members], in minor currency units.
 *
 * The parts always sum to exactly [totalMinor] — never a cent more or less.
 */
fun splitEqually(totalMinor: Long, members: List<MemberId>, salt: Long = 0): Map<MemberId, Long> {
    require(members.isNotEmpty()) { "cannot split $totalMinor between no one" }

    val count = members.size
    val base = totalMinor / count
    val spareCents = (totalMinor % count).toInt()
    val offset = (salt % count).toInt()

    return members.withIndex().associate { (index, member) ->
        // The spare-cent window is `spareCents` wide and starts at `offset`, wrapping round.
        val position = ((index - offset) % count + count) % count
        member to if (position < spareCents) base + 1 else base
    }
}
