package app.ledger.server.settlement

import app.ledger.server.payback.PaybackView
import java.util.UUID

/**
 * One row of the Settle-up screen: your position with one other person.
 *
 * [owedMinor] is positive when you owe them and negative when they owe you. Rows are bilateral by
 * design (§7a) — not the shortest set of transfers, which would pair up people who have no row on
 * this screen at all.
 */
data class SettlementRow(
    val memberId: UUID,
    val displayName: String,
    val personHue: Short,
    val owedMinor: Long,
    /**
     * Trip-level claims between the two of you that nobody has decided yet, in either direction.
     * A pending claim is why the row can read "sent for confirmation" while still counting as
     * unpaid — because that is exactly what it is.
     */
    val pending: List<PaybackView>,
    /**
     * Approved trip-level settlements between the two of you. These have already moved [owedMinor],
     * so they are a visible, undoable record rather than a live figure — the settle-up strip shows
     * them muted, with an undo, so a mistaken confirmation can be walked back (§7a).
     */
    val settled: List<PaybackView>,
    /**
     * Settlements *you* filed that they declined, newest last, carrying the reason they gave. An
     * item claim surfaces its rejection on the bill's own sheet; a trip-level settlement has no bill,
     * so without this the decline is filtered out of the payload and simply vanishes — the balance
     * reverts with nothing said and the claimant never learns why. This is that missing home.
     */
    val rejected: List<PaybackView>,
)

data class SettlementView(
    val rows: List<SettlementRow>,
    /** Positive means the group owes you. Equal to minus the sum of [rows], by construction. */
    val yourNetMinor: Long,
    /** Derived, never a button (§7a): everyone is square when no row has anything left on it. */
    val allSquare: Boolean,
)
