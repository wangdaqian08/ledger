package app.ledger.server.settlement

import app.ledger.server.payback.PaybackView
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

/**
 * One row of the Settle-up screen's "By person" mode: your position with one other person.
 *
 * [owedMinor] is positive when you owe them and negative when they owe you. Rows are bilateral by
 * design (§7a): they are what actually passed between the two of you, and they are where approvals
 * live. The shortest way to clear the trip is a different thing — [SettlementView.transfers], the
 * "By minimum transfer" mode — because it pairs up people who may have no debt between them at all.
 * Pay by that list and these rows can end up cancelling rather than zero, which is why
 * [SettlementView.allSquare] reads the net and not the rows.
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
    /**
     * Derived, never a button (§7a): you are square when your own net is zero — not when every row
     * is. After paying by [transfers], rows can cancel without zeroing (you owe Ben $15, Cat owes
     * you $15), and a rule that waited for every row would never call a correctly settled trip done.
     */
    val allSquare: Boolean,
    /**
     * The whole trip's fewest transfers — everyone's, not just yours — in the order the engine
     * produced them: exact pairs first, then largest against largest. Following it clears every
     * net. Derived on every read like everything else here; paying one is an ordinary settlement.
     */
    val transfers: List<TransferView>,
    /** "How it adds up" (§3): the figures behind every balance, for a person to check by hand. */
    val breakdown: BreakdownView,
)

/** One line of "By minimum transfer": [fromMemberId] pays [toMemberId] exactly [amountMinor]. */
data class TransferView(val fromMemberId: UUID, val toMemberId: UUID, val amountMinor: Long)

/**
 * "How it adds up" (§3): one row per person in roster order — the same from every seat bar
 * [BreakdownRowView.isYou] — and the totals beneath them. Every figure is the engine's `breakdown`,
 * totals included: the table exists so a person can check the arithmetic, and a total the browser
 * summed for itself would be checking the browser instead.
 */
data class BreakdownView(val rows: List<BreakdownRowView>, val totals: BreakdownTotalsView)

/** One person's figures: `paidMinor − shareMinor + settledMinor = netMinor`, on every row. */
data class BreakdownRowView(
    val memberId: UUID,
    val displayName: String,
    val personHue: Short,
    @get:JsonProperty("isYou") val isYou: Boolean,
    /** The full amount of every expense this person paid for. */
    val paidMinor: Long,
    /** Their portion of every expense, by its own split. */
    val shareMinor: Long,
    /** Approved paybacks sent less approved paybacks received, bill-linked and trip-level alike. */
    val settledMinor: Long,
    /** Their balance — the same figure as [SettlementView.yourNetMinor] from their own seat. */
    val netMinor: Long,
    /** How many By person rows this person has that are not zero. */
    val transfersByPerson: Int,
    /** How many lines of [SettlementView.transfers] this person is on, paying or paid. */
    val transfersFewest: Int,
)

/**
 * The totals row. Paid and share both equal the group spend; settled and net both equal zero.
 * [transfersByPerson] counts each non-zero pair once — not the sum of the rows, which sees every pair
 * from both ends — and [transfersFewest] is the plan's size.
 */
data class BreakdownTotalsView(
    val paidMinor: Long,
    val shareMinor: Long,
    val settledMinor: Long,
    val netMinor: Long,
    val transfersByPerson: Int,
    val transfersFewest: Int,
)

/** Same shape as app.ledger.server.trip.ClaimableMemberView — minimal member fields for display. */
data class FamilyMemberView(
    val id: UUID,
    val displayName: String,
    val personHue: Short,
    @get:JsonProperty("isYou") val isYou: Boolean,
)

/** One Family's position with one *other* Family in the partition — never an individual. */
data class FamilyCounterpartView(
    val members: List<FamilyMemberView>,
    /** Positive means this Family owes the counterpart; negative means the counterpart owes this Family. */
    val owedMinor: Long,
)

/** One Family in the completed partition: explicit, or an automatic singleton. */
data class FamilyView(
    val members: List<FamilyMemberView>,
    /** Positive means the Family is owed money overall. Equal to minus the sum of [counterparts], by construction. */
    val netMinor: Long,
    /** One row per *other* Family in the partition. */
    val counterparts: List<FamilyCounterpartView>,
)

/**
 * One line of "By minimum transfer" with Families built (§7a): Family [from] pays Family [to]
 * exactly [amountMinor], once, as one party each. Members settle among themselves; that is the
 * point of building a Family.
 */
data class FamilyTransferView(
    /** The paying Family, in roster order — any one of them may press Pay on the line. */
    val from: List<FamilyMemberView>,
    /** The Family being paid, in roster order. A payment goes to one of them: [payToMemberId]. */
    val to: List<FamilyMemberView>,
    val amountMinor: Long,
    /**
     * Who a payment on this line goes to, decided here so every viewer names the same person: the
     * first member of [to] in roster order whose account has a PayID — a party's PayID is the first
     * one among its members (PRD FR-07), and a payment needs somewhere to land — or, when none of
     * them has one, simply the first member of [to].
     */
    val payToMemberId: UUID,
    /**
     * Trip-level claims still awaiting a decision from any member of [from] to any member of [to],
     * newest last, read for the viewer. A Family has several phones: without these, a second member
     * would see an unpaid line and pay it again. Pending moves no number, so the line itself stays
     * until the claim is approved — this is what says it has already been sent.
     */
    val pending: List<PaybackView>,
)

data class FamiliesView(
    val families: List<FamilyView>,
    /**
     * The fewest-transfers plan between the Families in [families], in the engine's order: exact
     * pairs first, then largest against largest, ties on partition order. Built from each Family's
     * `netMinor`, so following it clears every one — and with no explicit Families it is
     * [SettlementView.transfers] line for line. Ephemeral like the partition it came from.
     */
    val transfers: List<FamilyTransferView>,
)
