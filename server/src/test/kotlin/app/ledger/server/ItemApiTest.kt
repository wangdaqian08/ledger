package app.ledger.server

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemApiTest : ApiTest() {
    @Test
    fun `any member can record what they spent, and the split is derived not stored`() {
        val trip = tripWith("Alice", "Bob")

        val item = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Dinner", 10_000, trip.category, trip.ownerMember, listOf(trip.ownerMember, trip.bob)),
        )

        assertEquals(HttpStatus.CREATED, item.statusCode)
        val splits = item.json()["splits"]
        assertEquals(2, splits.size())
        assertEquals(10_000, splits.sumOf { it["amountMinor"].asLong() })
        assertEquals(5_000, item.json()["yourShareMinor"].asLong())
        assertEquals("OPEN", item.json()["state"].asText())
    }

    @Test
    fun `an expense shared by nobody is refused`() {
        // The engine divides by the number of people on the list; an empty list is not a split
        // with no participants, it is an arithmetic error waiting to happen.
        val trip = tripWith("Alice", "Bob")

        val response = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Nothing", 10_000, trip.category, trip.ownerMember, emptyList()),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `somebody from another trip cannot be put on the list`() {
        val trip = tripWith("Alice", "Bob")
        val elsewhere = tripWith("Alice", "Bob")

        val response = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Dinner", 10_000, trip.category, trip.ownerMember, listOf(trip.ownerMember, elsewhere.bob)),
        )

        // A friendly 400 rather than the 500 the trip-scoped foreign key would otherwise produce.
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `the same person cannot appear on one expense twice`() {
        val trip = tripWith("Alice", "Bob")

        val response = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Dinner", 10_000, trip.category, trip.ownerMember, listOf(trip.bob, trip.bob)),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `exact amounts that do not add up are refused`() {
        val trip = tripWith("Alice", "Bob")

        val response = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Dinner", 10_000, trip.category, trip.ownerMember, emptyList(), splitRule = "EXACT") +
                mapOf(
                    "sharedBy" to listOf(
                        mapOf("memberId" to trip.ownerMember.toString(), "exactAmountMinor" to 4_000),
                        mapOf("memberId" to trip.bob.toString(), "exactAmountMinor" to 5_000),
                    ),
                ),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(
            response.body!!.contains("9000"),
            "the message should say what the amounts came to: ${response.body}",
        )
    }

    @Test
    fun `a weighted split hands out every cent`() {
        val trip = tripWith("Alice", "Bob")

        val item = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Taxi", 10_001, trip.category, trip.ownerMember, emptyList(), splitRule = "WEIGHTED") +
                mapOf(
                    "sharedBy" to listOf(
                        mapOf("memberId" to trip.ownerMember.toString(), "weight" to 2),
                        mapOf("memberId" to trip.bob.toString(), "weight" to 1),
                    ),
                ),
        )

        val splits = item.json()["splits"]
        assertEquals(10_001, splits.sumOf { it["amountMinor"].asLong() }, "a cent went missing")
        assertEquals(2, splits.count { it["weight"].asInt() > 0 }, "the weights should come back with the splits")
    }

    @Test
    fun `only the payer or the trip creator can change an expense`() {
        // Bob claims his slot, so he is a full member — and still cannot edit somebody else's bill.
        val trip = tripWith("Alice", "Bob")
        val bob = signedIn("Bob")
        bob.claim(trip.id, trip.owner.invite(trip.id), trip.bob)
        val item = trip.owner
            .post(
                "/api/trips/${trip.id}/items",
                expense("Dinner", 10_000, trip.category, trip.ownerMember, listOf(trip.ownerMember, trip.bob)),
            ).id()

        val edit = bob.patch("/api/items/$item", mapOf("amountMinor" to 1))
        val remove = bob.delete("/api/items/$item")

        assertEquals(HttpStatus.FORBIDDEN, edit.statusCode)
        assertEquals(HttpStatus.FORBIDDEN, remove.statusCode)
        // The 403 carries its reason, like the roster one does — not a bare status.
        assertTrue(edit.body!!.contains("Only the person who paid"), "the 403 threw away its reason: ${edit.body}")
        assertEquals(10_000, bob.get("/api/items/$item").json()["amountMinor"].asLong(), "but he can read it")
    }

    @Test
    fun `the person who paid can change their own expense`() {
        val trip = tripWith("Alice", "Bob")
        val bob = signedIn("Bob")
        bob.claim(trip.id, trip.owner.invite(trip.id), trip.bob)
        // Bob fronted this one, so it is his to correct even though Alice created the trip.
        val item = trip.owner
            .post(
                "/api/trips/${trip.id}/items",
                expense("Dinner", 10_000, trip.category, trip.bob, listOf(trip.ownerMember, trip.bob)),
            ).id()

        val edit = bob.patch("/api/items/$item", mapOf("amountMinor" to 20_000))

        assertEquals(HttpStatus.OK, edit.statusCode)
        assertEquals(10_000, edit.json()["yourShareMinor"].asLong())
    }

    @Test
    fun `a field left out of a patch is left alone`() {
        val trip = tripWith("Alice", "Bob")
        val item = trip.owner
            .post(
                "/api/trips/${trip.id}/items",
                expense("Dinner", 10_000, trip.category, trip.ownerMember, listOf(trip.ownerMember, trip.bob)),
            ).id()

        val patched = trip.owner.patch("/api/items/$item", mapOf("title" to "Late dinner"))

        assertEquals("Late dinner", patched.json()["title"].asText())
        assertEquals(10_000, patched.json()["amountMinor"].asLong(), "the amount should not have moved")
        assertEquals(2, patched.json()["splits"].size(), "the people list should not have moved")
    }

    @Test
    fun `deleting an expense takes its people list with it`() {
        val trip = tripWith("Alice", "Bob")
        val item = trip.owner
            .post(
                "/api/trips/${trip.id}/items",
                expense("Dinner", 10_000, trip.category, trip.ownerMember, listOf(trip.ownerMember, trip.bob)),
            ).id()

        assertEquals(HttpStatus.NO_CONTENT, trip.owner.delete("/api/items/$item").statusCode)

        assertEquals(HttpStatus.NOT_FOUND, trip.owner.get("/api/items/$item").statusCode)
        assertEquals(
            0,
            trip.owner
                .get("/api/trips/${trip.id}")
                .json()["items"]
                .size(),
        )
    }

    @Test
    fun `an expense on a trip you are not on is not readable`() {
        val trip = tripWith("Alice", "Bob")
        val item = trip.owner
            .post(
                "/api/trips/${trip.id}/items",
                expense("Dinner", 10_000, trip.category, trip.ownerMember, listOf(trip.ownerMember)),
            ).id()

        assertEquals(HttpStatus.NOT_FOUND, signedIn("Stranger").get("/api/items/$item").statusCode)
    }

    @Test
    fun `an expense cannot be filed under another trip's category`() {
        val trip = tripWith("Alice", "Bob")
        val elsewhere = tripWith("Alice", "Bob")
        val theirCategory = elsewhere.owner
            .post(
                "/api/trips/${elsewhere.id}/categories",
                mapOf("name" to "Ski passes", "icon" to "ticket", "hue" to 4),
            ).id()

        val response = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Lift pass", 10_000, theirCategory, trip.ownerMember, listOf(trip.ownerMember)),
        )

        // The one trip-scoping rule a foreign key cannot express, so the service holds it.
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `the client can choose the expense's id, and sending it twice does not double the expense`() {
        // The frontend mints the id so the split's salt is known before saving, which is what lets
        // the SplitBar show amounts that will not move once it lands. The same property makes a
        // retry after a dropped connection safe.
        val trip = tripWith("Alice", "Bob")
        val chosen = UUID.randomUUID()
        val body = expense("Dinner", 10_000, trip.category, trip.ownerMember, listOf(trip.ownerMember, trip.bob)) +
            mapOf("id" to chosen.toString())

        val first = trip.owner.post("/api/trips/${trip.id}/items", body)
        val replay = trip.owner.post("/api/trips/${trip.id}/items", body)

        assertEquals(HttpStatus.CREATED, first.statusCode)
        assertEquals(chosen, first.id())
        // 200, not 201: the request was answered, but nothing new happened.
        assertEquals(HttpStatus.OK, replay.statusCode)
        assertEquals(chosen, replay.id())
        assertEquals(
            1,
            trip.owner
                .get("/api/trips/${trip.id}")
                .json()["items"]
                .size(),
        )
    }

    @Test
    fun `an id already used by another trip is refused`() {
        val trip = tripWith("Alice", "Bob")
        val elsewhere = tripWith("Alice", "Bob")
        val chosen = UUID.randomUUID()
        elsewhere.owner.post(
            "/api/trips/${elsewhere.id}/items",
            expense("Theirs", 5_000, elsewhere.category, elsewhere.ownerMember, listOf(elsewhere.ownerMember)) +
                mapOf("id" to chosen.toString()),
        )

        val response = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Mine", 5_000, trip.category, trip.ownerMember, listOf(trip.ownerMember)) +
                mapOf("id" to chosen.toString()),
        )

        // Not a silent hand-over of somebody else's expense, and not a 404 either — the id is
        // genuinely taken.
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `two expenses whose ids differ only in the low bits stay two expenses`() {
        // Regression. engineItemId once folded only the UUID's high 64 bits, so these two became
        // one item inside the engine: one bill's paybacks were counted against the other, and a
        // bill nobody had paid reported ALL_SQUARE. UUIDv7 carries a millisecond timestamp in the
        // high bits, so two expenses added in the same moment collide by construction — and the
        // client is what mints these ids.
        val trip = tripWith("Alice", "Bob")
        val paid = "cccccccc-0000-4000-8000-000000000001"
        val unpaid = "cccccccc-0000-4000-8000-000000000002"

        listOf(paid, unpaid).forEach { id ->
            trip.owner.post(
                "/api/trips/${trip.id}/items",
                expense("Bill", 10_000, trip.category, trip.ownerMember, listOf(trip.ownerMember, trip.bob)) +
                    mapOf("id" to id),
            )
        }
        // Bob covers his half of one bill and none of the other.
        trip.owner.post(
            "/api/items/$paid/paybacks",
            mapOf("fromMemberId" to trip.bob.toString(), "amountMinor" to 5_000, "paidOn" to "2026-08-02"),
        )

        assertEquals(
            "ALL_SQUARE",
            trip.owner
                .get("/api/items/$paid")
                .json()["state"]
                .asText(),
        )
        assertEquals(
            "OPEN",
            trip.owner
                .get("/api/items/$unpaid")
                .json()["state"]
                .asText(),
            "one bill's paybacks settled another",
        )
    }

    @Test
    fun `a patch can turn an equal split into a weighted one, and it still hands out every cent`() {
        val trip = tripWith("Alice", "Bob")
        val item = trip.owner
            .post(
                "/api/trips/${trip.id}/items",
                expense("Dinner", 10_000, trip.category, trip.ownerMember, listOf(trip.ownerMember, trip.bob)),
            ).id()

        // The whole point of a patch's people list: change the rule and the inputs together, and the
        // split is re-derived — weights and all — with no stale share left over from the equal one.
        val patched = trip.owner.patch(
            "/api/items/$item",
            mapOf(
                "splitRule" to "WEIGHTED",
                "sharedBy" to listOf(
                    mapOf("memberId" to trip.ownerMember.toString(), "weight" to 2),
                    mapOf("memberId" to trip.bob.toString(), "weight" to 1),
                ),
            ),
        )

        assertEquals(HttpStatus.OK, patched.statusCode)
        val splits = patched.json()["splits"]
        assertEquals(10_000, splits.sumOf { it["amountMinor"].asLong() }, "a cent went missing changing the rule")
        val weightOf = splits.associate { it["memberId"].asText() to it["weight"].asInt() }
        assertEquals(2, weightOf[trip.ownerMember.toString()], "the weights should come back with the splits")
        assertEquals(1, weightOf[trip.bob.toString()])
    }

    @Test
    fun `a patch that empties the people list is refused, and the old split stands`() {
        // Create's @NotEmpty guards the list at creation; this is its hand-written twin in
        // ItemService.patch, because a patch's sharedBy is null-for-absent and an explicit `[]` is a
        // supplied value, not an omission — so bean validation never sees it.
        val trip = tripWith("Alice", "Bob")
        val item = trip.owner
            .post(
                "/api/trips/${trip.id}/items",
                expense("Dinner", 10_000, trip.category, trip.ownerMember, listOf(trip.ownerMember, trip.bob)),
            ).id()

        val emptied = trip.owner.patch("/api/items/$item", mapOf("sharedBy" to emptyList<Map<String, String>>()))

        assertEquals(HttpStatus.BAD_REQUEST, emptied.statusCode)
        val splits = trip.owner.get("/api/items/$item").json()["splits"]
        assertEquals(2, splits.size(), "the original people list must still stand")
        assertEquals(10_000, splits.sumOf { it["amountMinor"].asLong() })
    }

    @Test
    fun `an exact split missing one person's amount is refused`() {
        val trip = tripWith("Alice", "Bob")

        val response = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Dinner", 10_000, trip.category, trip.ownerMember, emptyList(), splitRule = "EXACT") +
                mapOf(
                    "sharedBy" to listOf(
                        mapOf("memberId" to trip.ownerMember.toString(), "exactAmountMinor" to 10_000),
                        // Bob's exact amount is left out entirely, so the amounts cannot be checked.
                        mapOf("memberId" to trip.bob.toString()),
                    ),
                ),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(
            response.body!!.contains("exact amount"),
            "the message should name what is missing: ${response.body}",
        )
    }

    @Test
    fun `a negative amount is refused on create and on patch, though zero is allowed`() {
        val trip = tripWith("Alice", "Bob")

        val created = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Refund?", -1, trip.category, trip.ownerMember, listOf(trip.ownerMember, trip.bob)),
        )
        assertEquals(HttpStatus.BAD_REQUEST, created.statusCode, "a bill cannot cost minus a cent")

        // Zero is deliberately fine — a comped item is on the books at nothing (@PositiveOrZero).
        val free = trip.owner
            .post(
                "/api/trips/${trip.id}/items",
                expense("Comped", 0, trip.category, trip.ownerMember, listOf(trip.ownerMember, trip.bob)),
            ).id()

        val patched = trip.owner.patch("/api/items/$free", mapOf("amountMinor" to -1))
        assertEquals(HttpStatus.BAD_REQUEST, patched.statusCode, "and it cannot be patched below zero either")
    }

    // --- helpers -----------------------------------------------------------------------------

    private class Fixture(
        val owner: SessionAwareClient,
        val id: UUID,
        val ownerMember: UUID,
        val bob: UUID,
        val category: UUID,
    )

    @Test
    fun `an exact amount below zero is refused even when the sum still matches`() {
        val trip = tripWith("Alice", "Bob")

        // 15000 − 5000 is exactly the 10000 total, so the sum check alone would wave this
        // through — and the database CHECK would then kill it as a bare 500.
        val response = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Dinner", 10_000, trip.category, trip.ownerMember, emptyList(), splitRule = "EXACT") +
                mapOf(
                    "sharedBy" to listOf(
                        mapOf("memberId" to trip.ownerMember.toString(), "exactAmountMinor" to 15_000),
                        mapOf("memberId" to trip.bob.toString(), "exactAmountMinor" to -5_000),
                    ),
                ),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(response.body!!.contains("-5000"), "the message should name the amount: ${response.body}")
    }

    @Test
    fun `an amount too large to multiply by its weights is refused, not answered wrongly`() {
        // Past Long.MAX_VALUE / weight the engine's products would wrap and the shares would come
        // back negative under a 200. The engine refuses; this pins that the API says why, as a 400.
        val trip = tripWith("Alice", "Bob")

        val response = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense(
                "Impossible",
                Long.MAX_VALUE,
                trip.category,
                trip.ownerMember,
                emptyList(),
                splitRule = "WEIGHTED",
            ) +
                mapOf(
                    "sharedBy" to listOf(
                        mapOf("memberId" to trip.ownerMember.toString(), "weight" to 2),
                        mapOf("memberId" to trip.bob.toString(), "weight" to 1),
                    ),
                ),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `an equal split too large to hold on the books is refused, not wrapped`() {
        // An EQUAL split skips the weighted overflow guard, so nothing stopped an amount that a
        // second expense could sum past Long.MAX_VALUE — at which point the trip's net wraps to a
        // huge negative under a 200, and both invariants break silently. Bound it at the input.
        val trip = tripWith("Alice", "Bob")

        val response = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Absurd", Long.MAX_VALUE, trip.category, trip.ownerMember, listOf(trip.ownerMember, trip.bob)),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `an amount larger than any real trip is refused, but a merely large one is not`() {
        val trip = tripWith("Alice", "Bob")
        val members = listOf(trip.ownerMember, trip.bob)

        val overCap = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Over", 1_000_000_000_001, trip.category, trip.ownerMember, members),
        )
        assertEquals(HttpStatus.BAD_REQUEST, overCap.statusCode, "one over the ceiling is refused")

        val atCap = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("At", 1_000_000_000_000, trip.category, trip.ownerMember, members),
        )
        assertEquals(HttpStatus.CREATED, atCap.statusCode, "a ten-billion-dollar expense is still allowed")
        assertEquals(1_000_000_000_000, atCap.json()["splits"].sumOf { it["amountMinor"].asLong() })
    }

    @Test
    fun `a blank title cannot be patched onto an expense`() {
        val trip = tripWith("Alice", "Bob")
        val item = trip.owner
            .post(
                "/api/trips/${trip.id}/items",
                expense("Dinner", 10_000, trip.category, trip.ownerMember, listOf(trip.ownerMember, trip.bob)),
            ).id()

        // Create refuses a blank name; a patch must too, or a corrected expense can be left nameless
        // on every screen.
        val blanked = trip.owner.patch("/api/items/$item", mapOf("title" to "   "))

        assertEquals(HttpStatus.BAD_REQUEST, blanked.statusCode)
        assertEquals(
            "Dinner",
            trip.owner
                .get("/api/items/$item")
                .json()["title"]
                .asText(),
            "the old name should stand",
        )
    }

    @Test
    fun `a weighted split may put a zero on somebody, and it costs them nothing`() {
        // Weight zero is a real input the engine, the browser port and the pinned split vectors all
        // honour: this person is on the bill for the record but owes nothing on it. The server was
        // the one layer refusing it.
        val trip = tripWith("Alice", "Bob")

        val item = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Cab", 10_000, trip.category, trip.ownerMember, emptyList(), splitRule = "WEIGHTED") +
                mapOf(
                    "sharedBy" to listOf(
                        mapOf("memberId" to trip.ownerMember.toString(), "weight" to 1),
                        mapOf("memberId" to trip.bob.toString(), "weight" to 0),
                    ),
                ),
        )

        assertEquals(HttpStatus.CREATED, item.statusCode)
        val byMember = item.json()["splits"].associate { it["memberId"].asText() to it["amountMinor"].asLong() }
        assertEquals(10_000, byMember[trip.ownerMember.toString()])
        assertEquals(0, byMember[trip.bob.toString()], "the zero-weight person owes nothing")
    }

    @Test
    fun `a weighted share with no weight at all is a zero, not a silent equal share`() {
        // Validation reads a missing weight as 0 ("on the bill, owing nothing"); the engine mapping
        // must agree. It once defaulted a null weight to 1, so an omitted weight validated as zero
        // and was then charged an equal share — the two ends disagreeing under a 200.
        val trip = tripWith("Alice", "Bob")

        val item = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Cab", 10_000, trip.category, trip.ownerMember, emptyList(), splitRule = "WEIGHTED") +
                mapOf(
                    "sharedBy" to listOf(
                        mapOf("memberId" to trip.ownerMember.toString(), "weight" to 1),
                        // Bob's weight is omitted entirely.
                        mapOf("memberId" to trip.bob.toString()),
                    ),
                ),
        )

        assertEquals(HttpStatus.CREATED, item.statusCode)
        val byMember = item.json()["splits"].associate { it["memberId"].asText() to it["amountMinor"].asLong() }
        assertEquals(10_000, byMember[trip.ownerMember.toString()])
        assertEquals(0, byMember[trip.bob.toString()], "an omitted weight owes nothing, as validation implied")
    }

    @Test
    fun `a negative weight is refused`() {
        val trip = tripWith("Alice", "Bob")

        val response = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Cab", 10_000, trip.category, trip.ownerMember, emptyList(), splitRule = "WEIGHTED") +
                mapOf(
                    "sharedBy" to listOf(
                        mapOf("memberId" to trip.ownerMember.toString(), "weight" to 1),
                        mapOf("memberId" to trip.bob.toString(), "weight" to -1),
                    ),
                ),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a weighted split where nobody carries any weight is refused`() {
        // Zero for everybody has nothing to divide by; somebody has to carry weight.
        val trip = tripWith("Alice", "Bob")

        val response = trip.owner.post(
            "/api/trips/${trip.id}/items",
            expense("Cab", 10_000, trip.category, trip.ownerMember, emptyList(), splitRule = "WEIGHTED") +
                mapOf(
                    "sharedBy" to listOf(
                        mapOf("memberId" to trip.ownerMember.toString(), "weight" to 0),
                        mapOf("memberId" to trip.bob.toString(), "weight" to 0),
                    ),
                ),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    private fun tripWith(ownerName: String, friend: String): Fixture {
        val owner = signedIn(ownerName)
        val tripId = owner.createTrip()
        val ownerMember = owner
            .get("/api/trips/$tripId")
            .json()["members"]
            .first { it["isYou"].asBoolean() }["id"]
            .asText()
        return Fixture(
            owner = owner,
            id = tripId,
            ownerMember = UUID.fromString(ownerMember),
            bob = owner.addMember(tripId, friend),
            category = owner.builtInCategory(tripId),
        )
    }
}
