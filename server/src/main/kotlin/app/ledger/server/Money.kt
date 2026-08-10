package app.ledger.server

/**
 * The largest a single amount may be, in minor units: ten billion dollars.
 *
 * Not a business limit — a safety rail. Every balance the engine derives is a sum of amounts into a
 * `Long`, and with no ceiling two large expenses could push that sum past `Long.MAX_VALUE`, at which
 * point it wraps to a huge negative and both invariants break under a 200. Ten billion leaves room
 * for any real trip while keeping millions of expenses well clear of the overflow — no reachable
 * number of them can sum past the limit.
 */
const val MAX_AMOUNT_MINOR = 1_000_000_000_000L
