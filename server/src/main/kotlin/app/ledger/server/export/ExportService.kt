package app.ledger.server.export

import app.ledger.server.trip.TripAccess
import app.ledger.server.trip.TripSnapshots
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The trip's outward spend as a file: one row per expense, for review long after the trip.
 *
 * Deliberately **expenses only** — money that left the group to the outside world. Who paid whom
 * back is the app's internal bookkeeping and is excluded, as are shares and participants: a
 * reviewer asking "what did we spend?" wants the spend journal, not the debt graph.
 *
 * Accuracy over convenience, everywhere:
 * - amounts are written in exact major units from integer minor units — string arithmetic, no
 *   floating point, no grouping separators, zero-decimal currencies without a fake ".00";
 * - `Recorded at` is the row's write timestamp, because the app captures the spend *date* only —
 *   a time of day next to the date would be an invention;
 * - RFC 4180 (CRLF, quoted fields with doubled quotes) with a UTF-8 BOM, so Excel opens 中文
 *   titles correctly instead of as mojibake.
 */
@Service
class ExportService(
    private val access: TripAccess,
    private val snapshots: TripSnapshots,
) {
    class Export(val filename: String, val csv: String)

    @Transactional(readOnly = true)
    fun expensesCsv(tripId: UUID, actor: UUID): Export {
        val trip = access.visibleTrip(tripId, actor)
        val snapshot = snapshots.load(tripId)
        val nameOf = snapshot.roster.associate { it.id to it.displayName }

        val rows = snapshot.items
            .sortedWith(compareBy({ it.spentOn }, { it.createdAt }, { it.id }))
            .map { item ->
                listOf(
                    item.spentOn.toString(),
                    RECORDED_AT.format(item.createdAt.truncatedTo(ChronoUnit.SECONDS).atZone(ZoneId.systemDefault())),
                    nameOf[item.payerMemberId] ?: "?",
                    item.title,
                    majorUnits(item.amountMinor, trip.currencyCode),
                    trip.currencyCode,
                    // Last, so the widest and least predictable cell cannot push the columns a
                    // reviewer reads first. No comment is an empty cell, never a missing one.
                    item.note.orEmpty(),
                )
            }

        val lines = (listOf(HEADER) + rows).joinToString(CRLF, postfix = CRLF) { line ->
            line.joinToString(",", transform = ::field)
        }
        return Export(filename = "${trip.name}-expenses.csv", csv = BOM + lines)
    }

    private companion object {
        val HEADER = listOf("Date", "Recorded at", "Paid by", "Item", "Amount", "Currency", "Comment")
        val RECORDED_AT: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        const val CRLF = "\r\n"

        /** So Excel reads the bytes as UTF-8 rather than guessing the local code page. */
        const val BOM = "\uFEFF"

        /**
         * Minor units per major unit, by ISO-4217 code — the same table as `web/src/lib/money.ts`,
         * because a CSV amount must read exactly as the screen showed it.
         */
        val FRACTION_DIGITS = mapOf(
            "JPY" to 0,
            "KRW" to 0,
            "VND" to 0,
            "CLP" to 0,
            "ISK" to 0,
            "BHD" to 3,
            "JOD" to 3,
            "KWD" to 3,
            "OMR" to 3,
            "TND" to 3,
        )

        /** Exact major units from integer minor units: `100001` AUD → `1000.01`, `10000` JPY → `10000`. */
        fun majorUnits(amountMinor: Long, currencyCode: String): String {
            val digits = FRACTION_DIGITS[currencyCode.uppercase()] ?: 2
            if (digits == 0) return amountMinor.toString()
            var scale = 1L
            repeat(digits) { scale *= 10 }
            val sign = if (amountMinor < 0) "-" else ""
            val abs = if (amountMinor < 0) -amountMinor else amountMinor
            return "$sign${abs / scale}.${(abs % scale).toString().padStart(digits, '0')}"
        }

        /**
         * A spreadsheet reads a cell that opens with one of these as a formula, not text. The three
         * free-text columns (the item title, the payer's name and the comment) are user-typed, so a
         * title of `=HYPERLINK("http://evil","click")` would execute on open in
         * Excel/Sheets/LibreOffice — CWE-1236, in the one file a reviewer downloads precisely to
         * trust the numbers. The comment is the widest of the three and the one most likely in
         * practice to hold a line break, so it leans hardest on the RFC 4180 quoting below. The tab
         * and carriage return are here because they let a leading `=` hide behind whitespace.
         *
         * A leading newline or space would hide an `=` just as well, and is deliberately *not* in
         * this set: it is closed on write instead. All three columns are stored trimmed — the title
         * and comment in `ItemService`, the name in `TripService` — and Kotlin's `trim` drops `\n`,
         * space and U+3000 alike, so no stored value can begin with whitespace at all. That
         * invariant is what makes the omission safe, and `ItemApiTest` pins it: relax a trim to keep
         * a comment's indentation without extending this set, and the hole opens.
         */
        val FORMULA_LEADS = setOf('=', '+', '-', '@', '\t', '\r')

        /**
         * RFC 4180 (a field holding a comma, quote or line break is quoted, its quotes doubled),
         * plus formula-injection defence: a value a spreadsheet would evaluate is prefixed with an
         * apostrophe, which every major spreadsheet reads as "this cell is text". The apostrophe is
         * added before the quoting check so a payload like `=1+1,2` is both neutralised and quoted.
         */
        fun field(value: String): String {
            val guarded = if (value.firstOrNull() in FORMULA_LEADS) "'$value" else value
            return if (guarded.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
                "\"${guarded.replace("\"", "\"\"")}\""
            } else {
                guarded
            }
        }
    }
}
