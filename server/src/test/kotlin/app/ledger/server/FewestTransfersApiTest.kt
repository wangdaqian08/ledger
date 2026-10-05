package app.ledger.server

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UC-1, "Weekend away" (spec S8), over HTTP from all five seats: the trip where plain greedy needs
 * four transfers and fewest transfers needs three, paid exactly as the list says, ending with every
 * net at zero while the By person rows still cancel rather than vanish.
 *
 * The engine pins the same numbers in `ScenariosTest`; this is the assurance that the adapter hands
 * them over intact — the list, its order, the PayIDs beside it, the square-overall rule, and the
 * "How it adds up" table a person would check it all against.
 */
class FewestTransfersApiTest : ApiTest() {
    @Test
    fun `weekend away - three transfers with PayIDs, paid, approved, square overall`() {
        // ---- Five people, all signed in, roster order Ann, Ben, Cat, Dan, Eve.
        val ann = signedIn("Ann")
        val tripId = ann.createTrip("Weekend away")
        val annM = ann.yourMemberId(tripId)
        val benM = ann.addMember(tripId, "Ben")
        val catM = ann.addMember(tripId, "Cat")
        val danM = ann.addMember(tripId, "Dan")
        val eveM = ann.addMember(tripId, "Eve")
        val invite = ann.invite(tripId)
        val ben = signedIn("Ben").also { it.claim(tripId, invite, benM) }
        val cat = signedIn("Cat").also { it.claim(tripId, invite, catM) }
        val dan = signedIn("Dan").also { it.claim(tripId, invite, danM) }
        val eve = signedIn("Eve").also { it.claim(tripId, invite, eveM) }
        val seats = listOf("Ann" to ann, "Ben" to ben, "Cat" to cat, "Dan" to dan, "Eve" to eve)

        assertEquals(HttpStatus.OK, ann.put("/api/me/pay-id", mapOf("payId" to "ann@example.com")).statusCode)
        assertEquals(HttpStatus.OK, cat.put("/api/me/pay-id", mapOf("payId" to "cat@example.com")).statusCode)

        // ---- Three $60 bills, every split even. Ids pinned anyway: the salt is the id (CLAUDE.md).
        val food = ann.builtInCategory(tripId)
        ann.addPinned(
            tripId,
            "eeeeeeee-0000-4000-8000-000000000001",
            expense("Breakfast", 6_000, food, annM, listOf(annM, benM, danM)),
        )
        ben.addPinned(
            tripId,
            "eeeeeeee-0000-4000-8000-000000000002",
            expense("Taxi", 6_000, food, benM, listOf(benM, catM, danM, eveM)),
        )
        cat.addPinned(
            tripId,
            "eeeeeeee-0000-4000-8000-000000000003",
            expense("Lunch", 6_000, food, catM, listOf(annM, catM, danM, eveM)),
        )

        val nets = mapOf("Ann" to 2_500L, "Ben" to 2_500L, "Cat" to 3_000L, "Dan" to -5_000L, "Eve" to -3_000L)
        val fewest = listOf(
            Triple(eveM, catM, 3_000L),                    // the exact pair, first
            Triple(danM, annM, 2_500L),                    // then greedy, Ann before Ben by roster
            Triple(danM, benM, 2_500L),
        )

        // ---- Every seat sees the same three transfers, in the same order, and its own net.
        for ((label, viewer) in seats) {
            val view = viewer.settlement(tripId)
            assertEquals(fewest, view.transfers(), "$label's transfers")
            assertEquals(nets.getValue(label), view["yourNetMinor"].asLong(), "$label's net")
        }

        // By person tells the longer story — Dan alone has three rows where the list has him pay twice.
        val dansRows = dan.settlement(tripId).rows()
        assertEquals(mapOf(annM to 2_000L, benM to 1_500L, catM to 1_500L, eveM to 0L), dansRows)

        // ---- How it adds up: paid − share + settled = net on every row, totals that tie out, and
        //      eight By person pairs against three lines of the plan.
        val before = ann.settlement(tripId)
        assertEquals(
            listOf(
                //    member     paid    share  settled      net  by person  fewest
                Adds(annM,      6_000,   3_500,       0,   2_500,         3,      1),
                Adds(benM,      6_000,   3_500,       0,   2_500,         4,      1),
                Adds(catM,      6_000,   3_000,       0,   3_000,         4,      1),
                Adds(danM,          0,   5_000,       0,  -5_000,         3,      2),
                Adds(eveM,          0,   3_000,       0,  -3_000,         2,      1),
            ),
            before.breakdownRows(),
        )
        assertEquals(Totals(18_000, 18_000, 0, 0, 8, 3), before.breakdownTotals())
        assertEquals(
            setOf(
                "memberId",
                "displayName",
                "personHue",
                "isYou",
                "paidMinor",
                "shareMinor",
                "settledMinor",
                "netMinor",
                "transfersByPerson",
                "transfersFewest",
            ),
            before["breakdown"]["rows"][0].fieldNames().asSequence().toSet(),
            "the wire shape of a row — isYou in particular, which Jackson would strip to `you`",
        )
        val members = ann.get("/api/trips/$tripId").json()["members"]
        val names = members.associate { it["id"].asText() to it["displayName"].asText() }
        before["breakdown"]["rows"].forEach {
            assertEquals(names.getValue(it["memberId"].asText()), it["displayName"].asText(), "a row names its member")
        }

        // The same table from every seat, bar whose row is marked as theirs.
        val seatMembers = mapOf("Ann" to annM, "Ben" to benM, "Cat" to catM, "Dan" to danM, "Eve" to eveM)
        for ((label, viewer) in seats) {
            val view = viewer.settlement(tripId)
            val yours = view["breakdown"]["rows"].filter { it["isYou"].asBoolean() }.map { it["memberId"].asText() }
            assertEquals(listOf(seatMembers.getValue(label).toString()), yours, "$label's own row")
            assertEquals(before.breakdownWithoutIsYou(), view.breakdownWithoutIsYou(), "$label sees a different table")
        }

        // ---- The PayIDs beside the recipients: Ann's and Cat's as entered, Ben has none.
        val roster = eve
            .get("/api/trips/$tripId")
            .json()["members"]
            .associateBy { UUID.fromString(it["id"].asText()) }
        assertEquals("cat@example.com", roster.getValue(catM)["payId"].asText())
        assertEquals("ann@example.com", roster.getValue(annM)["payId"].asText())
        assertTrue(roster.getValue(benM)["payId"].isNull, "Ben set no PayID")

        // ---- Each debtor pays their own transfers. Nothing moves while they wait.
        val claims = fewest.map { (from, to, amount) ->
            val payer = if (from == eveM) eve else dan
            val claim = payer.post(
                "/api/trips/$tripId/settlements",
                mapOf("toMemberId" to to.toString(), "amountMinor" to amount),
            )
            assertEquals("PENDING", claim.json()["status"].asText())
            to to claim.id()
        }
        for ((label, viewer) in seats) {
            val view = viewer.settlement(tripId)
            assertEquals(fewest, view.transfers(), "$label: a pending claim moved the transfers")
            assertEquals(nets.getValue(label), view["yourNetMinor"].asLong(), "$label: a pending claim moved a net")
        }

        // ---- The person owed approves each one.
        val recipients = mapOf(annM to ann, benM to ben, catM to cat)
        for ((to, claim) in claims) {
            val approved = recipients.getValue(to).post("/api/paybacks/$claim/approve", emptyMap<String, String>())
            assertEquals(HttpStatus.OK, approved.statusCode, "approving $claim: ${approved.body}")
        }

        // ---- Nothing left to transfer, everyone square — and invariant 2 over rows that cancel.
        for ((label, viewer) in seats) {
            val view = viewer.settlement(tripId)
            assertEquals(emptyList(), view.transfers(), "$label: transfers left over")
            assertEquals(0, view["yourNetMinor"].asLong(), "$label's net")
            assertTrue(view["allSquare"].asBoolean(), "$label is square overall")
            assertEquals(0, view.rows().values.sum(), "$label's rows must sum to minus net, which is zero")
        }

        // Eve paid Cat for the taxi share she owed Ben: on paper she still owes Ben, and Cat owes her.
        assertEquals(mapOf(annM to 0L, benM to 1_500L, catM to -1_500L, danM to 0L), eve.settlement(tripId).rows())
        assertEquals(mapOf(annM to -500L, benM to -1_000L, catM to 1_500L, eveM to 0L), dan.settlement(tripId).rows())

        // ---- The settled column is what squared everyone, and it sums to zero. Nothing is left on the
        //      plan, yet By person still counts eight pairs: the rows cancel, they do not vanish.
        val after = cat.settlement(tripId)
        assertEquals(
            listOf(
                //    member     paid    share  settled      net  by person  fewest
                Adds(annM,      6_000,   3_500,  -2_500,       0,         3,      0),
                Adds(benM,      6_000,   3_500,  -2_500,       0,         4,      0),
                Adds(catM,      6_000,   3_000,  -3_000,       0,         4,      0),
                Adds(danM,          0,   5_000,   5_000,       0,         3,      0),
                Adds(eveM,          0,   3_000,   3_000,       0,         2,      0),
            ),
            after.breakdownRows(),
        )
        assertEquals(Totals(18_000, 18_000, 0, 0, 8, 0), after.breakdownTotals())

        // ---- And neither can chase the rows that say they are owed: square overall is square.
        for ((label, viewer, them) in listOf(
            Triple("Eve", eve, catM),
            Triple("Dan", dan, annM),
            Triple("Dan", dan, benM),
        )) {
            val nudge = viewer.post("/api/trips/$tripId/remind", mapOf("memberId" to them.toString()))
            assertEquals(HttpStatus.BAD_REQUEST, nudge.statusCode, "$label reminding $them: ${nudge.body}")
        }
    }

    private fun SessionAwareClient.addPinned(tripId: UUID, id: String, expense: Map<String, Any>) {
        val created = post("/api/trips/$tripId/items", expense + mapOf("id" to id))
        assertEquals(id, created.id().toString(), "the pinned id did not stick")
    }

    private fun SessionAwareClient.settlement(tripId: UUID): JsonNode = get("/api/trips/$tripId/settlement").json()

    private fun JsonNode.transfers(): List<Triple<UUID, UUID, Long>> = this["transfers"].map {
        Triple(
            UUID.fromString(it["fromMemberId"].asText()),
            UUID.fromString(it["toMemberId"].asText()),
            it["amountMinor"].asLong(),
        )
    }

    private fun JsonNode.rows(): Map<UUID, Long> =
        this["rows"].associate { UUID.fromString(it["memberId"].asText()) to it["owedMinor"].asLong() }

    /** One row of How it adds up, as the figures a person would check. */
    private data class Adds(
        val member: UUID,
        val paid: Long,
        val share: Long,
        val settled: Long,
        val net: Long,
        val byPerson: Int,
        val fewest: Int,
    )

    /** How it adds up's totals: paid, share, settled, net, then the pairs and the plan's size. */
    private data class Totals(
        val paid: Long,
        val share: Long,
        val settled: Long,
        val net: Long,
        val byPerson: Int,
        val fewest: Int,
    )

    private fun JsonNode.breakdownRows(): List<Adds> = this["breakdown"]["rows"].map {
        Adds(
            UUID.fromString(it["memberId"].asText()),
            it["paidMinor"].asLong(),
            it["shareMinor"].asLong(),
            it["settledMinor"].asLong(),
            it["netMinor"].asLong(),
            it["transfersByPerson"].asInt(),
            it["transfersFewest"].asInt(),
        )
    }

    private fun JsonNode.breakdownTotals(): Totals = this["breakdown"]["totals"].let {
        Totals(
            it["paidMinor"].asLong(),
            it["shareMinor"].asLong(),
            it["settledMinor"].asLong(),
            it["netMinor"].asLong(),
            it["transfersByPerson"].asInt(),
            it["transfersFewest"].asInt(),
        )
    }

    /** The whole breakdown as sent, less the one field that differs by seat. */
    private fun JsonNode.breakdownWithoutIsYou(): JsonNode = this["breakdown"].deepCopy<JsonNode>().also { copy ->
        copy["rows"].forEach { (it as ObjectNode).remove("isYou") }
    }
}
