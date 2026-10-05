package app.ledger.server

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The "By minimum transfer" mode with Families built (§7a, "With Families"), over HTTP: the plan
 * `POST /api/trips/{id}/families` returns beside the Family cards, where each Family is one party.
 *
 * The engine pins the arithmetic in `ScenariosTest` (S9) and `FamilyTest`; this is the assurance the
 * adapter hands it over intact — members in roster order, the person a payment goes to, and any
 * claim already pending on a line, so a second member of a Family cannot pay it twice.
 *
 * Every item id is pinned, each test its own: the salt is the id (CLAUDE.md), and one Postgres
 * serves the whole suite, so two tests sharing a pinned id would be two trips claiming one item.
 */
class FewestTransfersFamilyApiTest : ApiTest() {
    @Test
    fun `AC-07 - a family pays as one party`() {
        val trip = tripOf("Ann", "Ben", "Cat", "Dan")
        val (ann, ben, cat, dan) = trip.ids("Ann", "Ben", "Cat", "Dan")
        trip.add(
            "Ann",
            "fa000001-0000-4000-8000-000000000001",
            expense("Hotel", 40_000, trip.food, ann, listOf(ann, ben, cat, dan)),
        )

        val view = trip.client("Ann").families(trip.id, listOf(ben, cat))

        assertEquals(
            listOf(
                Line(from = listOf(ben, cat), to = listOf(ann), amountMinor = 20_000, payTo = ann),
                Line(from = listOf(dan), to = listOf(ann), amountMinor = 10_000, payTo = ann),
            ),
            view.lines(),
        )
        view["transfers"].forEach { assertEquals(0, it["pending"].size(), "nothing has been claimed yet") }
    }

    @Test
    fun `UC-2 - Ann and Ben as one family, paid to the first of them with a PayID`() {
        val trip = weekendAway("fa000002")
        val (ann, ben, cat, dan, eve) = trip.ids("Ann", "Ben", "Cat", "Dan", "Eve")
        trip.setPayId("Ann", "ann@example.com")

        // Two lines where per person needs three, members of each side in roster order.
        assertEquals(
            listOf(
                Line(from = listOf(dan), to = listOf(ann, ben), amountMinor = 5_000, payTo = ann),
                Line(from = listOf(eve), to = listOf(cat), amountMinor = 3_000, payTo = cat),
            ),
            trip.client("Dan").families(trip.id, listOf(ann, ben)).lines(),
        )

        // Both have one: the first in roster order, which is Ann.
        trip.setPayId("Ben", "ben@example.com")
        assertEquals(ann, trip.payToOnFirstLine(listOf(ann, ben)))

        // Only Ben has one: a payment to the Family goes to the member who can actually receive it.
        trip.setPayId("Ann", null)
        assertEquals(ben, trip.payToOnFirstLine(listOf(ann, ben)))

        // Nobody has one: the first member in roster order, still Ann.
        trip.setPayId("Ben", "   ")
        assertEquals(ann, trip.payToOnFirstLine(listOf(ann, ben)))
    }

    @Test
    fun `a claim on a family's line is shown to every member, and moves nothing until approved`() {
        val trip = weekendAway("fa000003")
        val (ann, ben, cat, dan, eve) = trip.ids("Ann", "Ben", "Cat", "Dan", "Eve")
        val plan = listOf(
            Line(from = listOf(dan, eve), to = listOf(cat), amountMinor = 3_000, payTo = cat),
            Line(from = listOf(dan, eve), to = listOf(ann), amountMinor = 2_500, payTo = ann),
            Line(from = listOf(dan, eve), to = listOf(ben), amountMinor = 2_500, payTo = ben),
        )
        assertEquals(plan, trip.client("Eve").families(trip.id, listOf(dan, eve)).lines())

        // Dan pays the Family's line to Cat from his own phone.
        val claim = trip.client("Dan").post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to cat.toString(), "amountMinor" to 3_000),
        )
        assertEquals(HttpStatus.CREATED, claim.statusCode, "Dan's claim: ${claim.body}")
        val claimId = claim.id()
        // A claim against the lunch bill is between the same two people but is not a settlement, so
        // it must not be mistaken for a payment on the line.
        val lunch = UUID.fromString("fa000003-0000-4000-8000-000000000003")
        val billClaim = trip.client("Dan").post(
            "/api/items/$lunch/paybacks",
            mapOf("fromMemberId" to dan.toString(), "amountMinor" to 1_500, "paidOn" to "2026-08-02"),
        )
        assertEquals(HttpStatus.CREATED, billClaim.statusCode, "Dan's bill claim: ${billClaim.body}")

        // Eve, the other half of the Family, sees Dan's claim on that line — and the plan unchanged.
        val evesView = trip.client("Eve").families(trip.id, listOf(dan, eve))
        assertEquals(plan, evesView.lines(), "a pending claim moved the plan")
        val pending = evesView["transfers"][0]["pending"]
        assertEquals(1, pending.size(), "the line to Cat should carry Dan's claim: $pending")
        assertEquals(claimId.toString(), pending[0]["id"].asText())
        assertEquals("PENDING", pending[0]["status"].asText())
        assertEquals(dan.toString(), pending[0]["fromMemberId"].asText())
        assertEquals(cat.toString(), pending[0]["toMemberId"].asText())
        assertEquals(3_000, pending[0]["amountMinor"].asLong())
        // Read for the viewer: Eve is neither owed it nor the creator, nor a party to it.
        assertFalse(pending[0]["viewerCanDecide"].asBoolean(), "Eve cannot decide Cat's claim")
        assertFalse(pending[0]["viewerCanUndo"].asBoolean(), "Eve is not a party to Dan's claim")
        assertEquals(0, evesView["transfers"][1]["pending"].size())
        assertEquals(0, evesView["transfers"][2]["pending"].size())

        // The same line for Cat, who is owed it, offers the decision.
        val catsView = trip.client("Cat").families(trip.id, listOf(dan, eve))
        assertTrue(catsView["transfers"][0]["pending"][0]["viewerCanDecide"].asBoolean(), "Cat decides")

        // Once Cat approves, the line is paid and the plan recomputes without it.
        val approved = trip.client("Cat").post("/api/paybacks/$claimId/approve", emptyMap<String, String>())
        assertEquals(HttpStatus.OK, approved.statusCode, "approving: ${approved.body}")
        assertEquals(plan.drop(1), trip.client("Eve").families(trip.id, listOf(dan, eve)).lines())
    }

    @Test
    fun `on a mixed trip the family plan clears every family's net, whatever the partition`() {
        val trip = tripOf("Ann", "Ben", "Cat", "Dan", "Eve")
        val (ann, ben, cat, dan, eve) = trip.ids("Ann", "Ben", "Cat", "Dan", "Eve")
        // Five payers, splits that leave spare cents, an approved settlement and a pending one.
        trip.add(
            "Ann",
            "fa000004-0000-4000-8000-000000000001",
            expense("Dinner", 10_001, trip.food, ann, listOf(ann, ben, cat, dan, eve)),
        )
        trip.add(
            "Ben",
            "fa000004-0000-4000-8000-000000000002",
            expense("Taxi", 7_001, trip.food, ben, listOf(ben, cat, eve)),
        )
        trip.add(
            "Cat",
            "fa000004-0000-4000-8000-000000000003",
            expense("Lunch", 3_333, trip.food, cat, listOf(ann, dan)),
        )
        trip.add(
            "Dan",
            "fa000004-0000-4000-8000-000000000004",
            expense("Coffee", 999, trip.food, dan, listOf(ann, ben, cat)),
        )
        trip.add(
            "Eve",
            "fa000004-0000-4000-8000-000000000005",
            expense("Snacks", 2_500, trip.food, eve, listOf(eve, ben)),
        )
        val settled = trip.client("Dan").post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to ann.toString(), "amountMinor" to 1_000),
        )
        val approved = trip.client("Ann").post("/api/paybacks/${settled.id()}/approve", emptyMap<String, String>())
        assertEquals(HttpStatus.OK, approved.statusCode, "approving Dan's settlement: ${approved.body}")
        trip.client("Eve").post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to ben.toString(), "amountMinor" to 777),
        )

        val partitions = listOf(
            emptyList(),
            listOf(listOf(ben, dan)),
            listOf(listOf(ann, cat), listOf(dan, eve)),
            listOf(listOf(eve, ben, cat)),
        )
        for (explicit in partitions) {
            val view = trip.client("Eve").families(trip.id, *explicit.toTypedArray())
            val netOf = view["families"].associate { it["members"].ids().toSet() to it["netMinor"].asLong() }
            val lines = view.lines()

            netOf.forEach { (family, net) ->
                val received = lines.filter { it.to.toSet() == family }.sumOf { it.amountMinor }
                val sent = lines.filter { it.from.toSet() == family }.sumOf { it.amountMinor }
                assertEquals(net, received - sent, "$explicit: $family is not square after the plan")
            }
            lines.forEach { assertTrue(it.amountMinor > 0, "$explicit: a line of zero or less") }
            assertTrue(
                lines.size <= maxOf(netOf.values.count { it != 0L } - 1, 0),
                "$explicit: ${lines.size} lines for ${netOf.values.count { it != 0L }} families not square",
            )
            assertEquals(
                emptySet(),
                lines.map { it.from.toSet() }.toSet() intersect lines.map { it.to.toSet() }.toSet(),
                "$explicit: a family both sends and receives",
            )
        }

        // With nothing built, the Family plan is the per-person plan GET /settlement serves, line for line.
        val perPerson = trip.client("Eve").get("/api/trips/${trip.id}/settlement").json()["transfers"].map {
            Triple(
                listOf(UUID.fromString(it["fromMemberId"].asText())),
                listOf(UUID.fromString(it["toMemberId"].asText())),
                it["amountMinor"].asLong(),
            )
        }
        assertTrue(perPerson.isNotEmpty(), "the trip should need some transfers")
        assertEquals(
            perPerson,
            trip
                .client("Eve")
                .families(trip.id)
                .lines()
                .map { Triple(it.from, it.to, it.amountMinor) },
        )
    }

    // --- helpers -----------------------------------------------------------------------------

    /** One line of the plan as the client reads it: member ids on each side, in response order. */
    private data class Line(val from: List<UUID>, val to: List<UUID>, val amountMinor: Long, val payTo: UUID)

    private inner class Seats(
        val id: UUID,
        private val clients: Map<String, SessionAwareClient>,
        private val members: Map<String, UUID>,
    ) {
        val food: UUID by lazy { client(clients.keys.first()).builtInCategory(id) }

        fun client(name: String): SessionAwareClient = clients.getValue(name)

        fun ids(vararg names: String): List<UUID> = names.map(members::getValue)

        fun add(payer: String, pinnedId: String, expense: Map<String, Any>) {
            val created = client(payer).post("/api/trips/$id/items", expense + mapOf("id" to pinnedId))
            assertEquals(pinnedId, created.id().toString(), "the pinned id did not stick")
        }

        fun setPayId(name: String, payId: String?) {
            val response = client(name).put("/api/me/pay-id", mapOf("payId" to payId))
            assertEquals(HttpStatus.OK, response.statusCode, "setting $name's PayID: ${response.body}")
        }

        fun payToOnFirstLine(explicit: List<UUID>): UUID =
            client(clients.keys.last())
                .families(id, explicit)
                .lines()
                .first()
                .payTo
    }

    /** Everyone named, in roster order, each signed in and holding their own seat. */
    private fun tripOf(vararg names: String): Seats {
        val creator = signedIn(names.first())
        val tripId = creator.createTrip()
        val members = linkedMapOf(names.first() to creator.yourMemberId(tripId))
        names.drop(1).forEach { members[it] = creator.addMember(tripId, it) }
        val invite = creator.invite(tripId)
        val clients = linkedMapOf(names.first() to creator)
        names.drop(1).forEach { name ->
            val client = signedIn(name)
            val claimed = client.claim(tripId, invite, members.getValue(name))
            assertEquals(HttpStatus.OK, claimed.statusCode, "$name claiming their seat: ${claimed.body}")
            clients[name] = client
        }
        return Seats(tripId, clients, members)
    }

    /** S8's weekend away: Breakfast, Taxi and Lunch at $60 each, item ids under [prefix]. */
    private fun weekendAway(prefix: String): Seats {
        val trip = tripOf("Ann", "Ben", "Cat", "Dan", "Eve")
        val (ann, ben, cat, dan, eve) = trip.ids("Ann", "Ben", "Cat", "Dan", "Eve")
        val breakfast = expense("Breakfast", 6_000, trip.food, ann, listOf(ann, ben, dan))
        val taxi = expense("Taxi", 6_000, trip.food, ben, listOf(ben, cat, dan, eve))
        val lunch = expense("Lunch", 6_000, trip.food, cat, listOf(ann, cat, dan, eve))
        trip.add("Ann", "$prefix-0000-4000-8000-000000000001", breakfast)
        trip.add("Ben", "$prefix-0000-4000-8000-000000000002", taxi)
        trip.add("Cat", "$prefix-0000-4000-8000-000000000003", lunch)
        return trip
    }

    private fun SessionAwareClient.families(tripId: UUID, vararg explicit: List<UUID>): JsonNode {
        val response = post(
            "/api/trips/$tripId/families",
            mapOf("families" to explicit.map { family -> mapOf("memberIds" to family.map { it.toString() }) }),
        )
        assertEquals(HttpStatus.OK, response.statusCode, "previewing families: ${response.body}")
        return response.json()
    }

    private fun JsonNode.lines(): List<Line> = this["transfers"].map {
        Line(
            from = it["from"].ids(),
            to = it["to"].ids(),
            amountMinor = it["amountMinor"].asLong(),
            payTo = UUID.fromString(it["payToMemberId"].asText()),
        )
    }

    private fun JsonNode.ids(): List<UUID> = map { UUID.fromString(it["id"].asText()) }
}
