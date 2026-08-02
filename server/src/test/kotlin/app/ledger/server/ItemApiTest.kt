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

    // --- helpers -----------------------------------------------------------------------------

    private class Fixture(
        val owner: SessionAwareClient,
        val id: UUID,
        val ownerMember: UUID,
        val bob: UUID,
        val category: UUID,
    )

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
