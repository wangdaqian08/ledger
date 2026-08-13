package app.ledger.server

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDate
import kotlin.test.assertEquals
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
        assertEquals("Date,Recorded at,Paid by,Item,Amount,Currency", lines.first())
        assertEquals(3, lines.size, "a header and one row per expense — the payback must not appear")

        // 100001 minor units is exactly 1000.01 — never 1000.0100000000001.
        assertTrue(lines[1].endsWith(",Hotel,1000.01,AUD"), "was: ${lines[1]}")
        // The awkward title arrives quoted with its quotes doubled, and the amount still parses.
        assertTrue(lines[2].endsWith(",\"Taxi, \"\"airport\"\"\",12.34,AUD"), "was: ${lines[2]}")
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
        assertTrue(lines[1].endsWith(",Ryokan,10000,JPY"), "was: ${lines[1]}")
    }

    @Test
    fun `the export is as private as the trip - a stranger gets 404`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()

        assertEquals(HttpStatus.NOT_FOUND, signedIn("Stranger").get("/api/trips/$tripId/expenses.csv").statusCode)
    }

    private fun SessionAwareClient.memberId(tripId: java.util.UUID): java.util.UUID =
        java.util.UUID.fromString(
            get("/api/trips/$tripId")
                .json()["members"]
                .first { it["isYou"].asBoolean() }["id"]
                .asText(),
        )
}
