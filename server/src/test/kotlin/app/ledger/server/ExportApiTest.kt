package app.ledger.server

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The expense export: the trip's outward spend as a CSV a reviewer can trust.
 *
 * The promises under test are the ones the file exists for — every amount exact, expenses only
 * (never the internal who-paid-who), fields escaped so a title cannot shift columns, and the same
 * visibility rule as the trip itself.
 */
class ExportApiTest : ApiTest() {
    @Test
    fun `exports each expense with its exact amount, and no payback rows`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Kyoto")
        val aliceMember = alice.memberId(tripId)
        val bobMember = alice.addMember(tripId, "Bob")
        val category = alice.builtInCategory(tripId)

        alice.post(
            "/api/trips/$tripId/items",
            expense("Hotel", 100_001, category, aliceMember, listOf(aliceMember, bobMember)),
        )
        val taxi = alice
            .post(
                "/api/trips/$tripId/items",
                expense(
                    // A comma and quotes: unescaped, this title would shift every later column.
                    "Taxi, \"airport\"",
                    1_234,
                    category,
                    aliceMember,
                    listOf(aliceMember, bobMember),
                    spentOn = LocalDate.of(2026, 8, 2),
                ),
            ).id()
        // An approved repayment against the taxi: internal money movement, not outward spend.
        val claim = alice
            .post(
                "/api/items/$taxi/paybacks",
                mapOf("fromMemberId" to bobMember.toString(), "amountMinor" to 617, "paidOn" to "2026-08-03"),
            ).id()
        alice.post("/api/paybacks/$claim/approve", emptyMap<String, String>())

        val response = alice.get("/api/trips/$tripId/expenses.csv")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("text/csv;charset=UTF-8", response.headers.contentType.toString())
        assertTrue(
            response.headers.contentDisposition
                .toString()
                .contains("expenses.csv"),
            "a download, named for what it holds",
        )

        val body = response.body!!
        assertTrue(body.startsWith("\uFEFF"), "UTF-8 BOM, so Excel does not guess the encoding")
        val lines = body.removePrefix("\uFEFF").split("\r\n").filter { it.isNotEmpty() }
        assertEquals("Date,Recorded at,Paid by,Item,Amount,Currency,Comment", lines.first())
        assertEquals(3, lines.size, "a header and one row per expense — the payback must not appear")

        // 100001 minor units is exactly 1000.01 — never 1000.0100000000001. Neither expense carries
        // a comment, so each row ends on the empty last cell rather than dropping the column.
        assertTrue(lines[1].endsWith(",Hotel,1000.01,AUD,"), "was: ${lines[1]}")
        // The awkward title arrives quoted with its quotes doubled, and the amount still parses.
        assertTrue(lines[2].endsWith(",\"Taxi, \"\"airport\"\"\",12.34,AUD,"), "was: ${lines[2]}")
        assertTrue(lines[1].startsWith("2026-08-01,"), "rows in spend-date order")
        assertTrue(lines[2].startsWith("2026-08-02,"))
    }

    @Test
    fun `a zero-decimal currency exports whole amounts, not an invented fraction`() {
        val alice = signedIn("Alice")
        val tripId = alice
            .post("/api/trips", newTrip("Tokyo") + mapOf("currencyCode" to "JPY"))
            .id()
        val aliceMember = alice.memberId(tripId)

        alice.post(
            "/api/trips/$tripId/items",
            expense("Ryokan", 10_000, alice.builtInCategory(tripId), aliceMember, listOf(aliceMember)),
        )

        val lines = alice
            .get("/api/trips/$tripId/expenses.csv")
            .body!!
            .removePrefix("\uFEFF")
            .split("\r\n")
        // 10,000 yen IS 10000 minor units (spec §2 S5): no ".00" that would read as 100 yen ×100.
        assertTrue(lines[1].endsWith(",Ryokan,10000,JPY,"), "was: ${lines[1]}")
    }

    @Test
    fun `an expense's comment reaches its row, and one without leaves the cell empty`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Nagano")
        val aliceMember = alice.memberId(tripId)
        val category = alice.builtInCategory(tripId)

        alice.post(
            "/api/trips/$tripId/items",
            expense("Onsen", 4_000, category, aliceMember, listOf(aliceMember)) +
                mapOf("note" to "Towels were extra"),
        )
        alice.post(
            "/api/trips/$tripId/items",
            expense("Beer", 800, category, aliceMember, listOf(aliceMember), spentOn = LocalDate.of(2026, 8, 2)),
        )

        val lines = alice
            .get("/api/trips/$tripId/expenses.csv")
            .body!!
            .removePrefix("")
            .split("\r\n")
            .filter { it.isNotEmpty() }

        assertTrue(lines[1].endsWith(",Onsen,40.00,AUD,Towels were extra"), "was: ${lines[1]}")
        // No comment is an empty cell, not a missing one — every row has to carry every column.
        assertTrue(lines[2].endsWith(",Beer,8.00,AUD,"), "was: ${lines[2]}")
    }

    @Test
    fun `a comment that opens like a formula is neutralised, and its commas and newlines stay in one cell`() {
        // The comment is the widest user-typed cell in the file — up to 1200 characters — and the
        // one most likely in practice to hold a line break, so it gets the same guard as the title.
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Hakone")
        val aliceMember = alice.memberId(tripId)
        val category = alice.builtInCategory(tripId)

        alice.post(
            "/api/trips/$tripId/items",
            expense("Lunch", 900, category, aliceMember, listOf(aliceMember)) +
                mapOf("note" to "=HYPERLINK(\"http://evil\",\"x\")"),
        )
        alice.post(
            "/api/trips/$tripId/items",
            expense("Dinner", 1_100, category, aliceMember, listOf(aliceMember), spentOn = LocalDate.of(2026, 8, 2)) +
                mapOf("note" to "split three ways, \"roughly\"\r\nBob paid the tip"),
        )

        val body = alice.get("/api/trips/$tripId/expenses.csv").body!!
        val records = records(body)

        assertEquals(3, records.size, "a header and one row each — the newline comment added no record")
        assertFalse(body.contains(",=HYPERLINK"), "a formula comment must never reach a cell unguarded")
        assertTrue(body.contains("'=HYPERLINK"), "the '=' lead is neutralised with a leading apostrophe")
        assertTrue(
            body.contains("\"split three ways, \"\"roughly\"\"\r\nBob paid the tip\""),
            "the comma, doubled quotes and newline all stay inside one quoted cell: $body",
        )
    }

    @Test
    fun `a title that looks like a formula is neutralised, and a newline stays one record`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Sapporo")
        val aliceMember = alice.memberId(tripId)
        val category = alice.builtInCategory(tripId)

        alice.post(
            "/api/trips/$tripId/items",
            // Opens with '=' and carries a URL: a live HYPERLINK the moment Excel opens the file.
            expense("=HYPERLINK(\"http://evil\",\"x\")", 500, category, aliceMember, listOf(aliceMember)),
        )
        alice.post(
            "/api/trips/$tripId/items",
            // A newline in a title must not split one expense across two CSV records.
            expense(
                "line one\r\nline two",
                600,
                category,
                aliceMember,
                listOf(aliceMember),
                spentOn = LocalDate.of(2026, 8, 2),
            ),
        )

        val body = alice.get("/api/trips/$tripId/expenses.csv").body!!
        val records = records(body)

        assertEquals(3, records.size, "a header and one row each — the newline title added no record")
        assertFalse(body.contains(",=HYPERLINK"), "a formula title must never reach a cell unguarded")
        assertTrue(body.contains("'=HYPERLINK"), "the '=' lead is neutralised with a leading apostrophe")
        assertTrue(body.contains("\"line one\r\nline two\""), "the newline title is quoted, not split")
    }

    @Test
    fun `a payer name that opens like a formula, or holds a comma, is neutralised and quoted`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip("Otaru")
        // A name that is both a formula lead and comma-bearing: injection and column-shift at once.
        val evil = alice.addMember(tripId, "=cmd,Bob")
        val category = alice.builtInCategory(tripId)
        alice.post(
            "/api/trips/$tripId/items",
            expense("Snacks", 300, category, evil, listOf(evil)),
        )

        val body = alice.get("/api/trips/$tripId/expenses.csv").body!!
        assertFalse(body.contains(",=cmd"), "the payer name must not reach a cell as a live formula")
        assertTrue(body.contains("\"'=cmd,Bob\""), "guarded with an apostrophe and quoted for the comma")
    }

    @Test
    fun `the export is as private as the trip - a stranger gets 404`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()

        assertEquals(HttpStatus.NOT_FOUND, signedIn("Stranger").get("/api/trips/$tripId/expenses.csv").statusCode)
    }

    /**
     * The file's records, split the way RFC 4180 defines one: at a CRLF that is *outside* quotes.
     *
     * A plain `split("\r\n")` cannot tell a record boundary from a line break somebody typed into a
     * comment, so it counts the same records whether the quoting works or not — which makes any
     * assertion on the count vacuous. This is the parse a spreadsheet does, and the only one under
     * which "the line break added no record" means anything.
     */
    private fun records(body: String): List<String> {
        val records = mutableListOf<String>()
        val record = StringBuilder()
        var quoted = false
        var i = 0
        while (i < body.length) {
            val char = body[i]
            if (!quoted && char == '\r' && body.getOrNull(i + 1) == '\n') {
                records += record.toString()
                record.clear()
                i += 2
            } else {
                if (char == '"') quoted = !quoted
                record.append(char)
                i++
            }
        }
        records += record.toString()
        return records.filter { it.isNotEmpty() }.map { it.removePrefix("") }
    }

    private fun SessionAwareClient.memberId(tripId: java.util.UUID): java.util.UUID =
        java.util.UUID.fromString(
            get("/api/trips/$tripId")
                .json()["members"]
                .first { it["isYou"].asBoolean() }["id"]
                .asText(),
        )
}
