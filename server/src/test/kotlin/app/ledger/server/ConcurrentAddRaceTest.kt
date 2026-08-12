package app.ledger.server

import app.ledger.server.category.CategoryRepository
import app.ledger.server.trip.TripMemberRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import kotlin.test.assertEquals

/**
 * Adding a member and adding a category both check the name is free, then insert — so two adds of
 * the same name at once can both pass the check. The database's unique index lets only one keep it;
 * the other must land as a 409 the client can act on, not a bare 500.
 *
 * The race is forced deterministically, as in [ItemCreateRaceTest]: the name pre-check is blinded,
 * so the add drives straight into the real unique constraint the way a genuine concurrent loser
 * would. The constraint, the rollback and the translated 409 are all real.
 */
class ConcurrentAddRaceTest : ApiTest() {
    @MockitoSpyBean
    private lateinit var members: TripMemberRepository

    @MockitoSpyBean
    private lateinit var categories: CategoryRepository

    @Test
    fun `two adds of the same member name at once become one 409, not a 500`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        alice.addMember(tripId, "Bob")

        // Blind the name pre-check so the next add skips the early 409 and meets the unique index.
        Mockito.doReturn(false).`when`(members).existsByTripIdAndDisplayNameIgnoreCase(tripId, "Bob")

        val racing = alice.post("/api/trips/$tripId/members", mapOf("displayName" to "Bob"))

        assertEquals(HttpStatus.CONFLICT, racing.statusCode, "the race loser must be a 409, not a 500")
        assertEquals(
            2, // Alice's own slot plus one Bob
            alice.get("/api/trips/$tripId").json()["members"].size(),
            "and the name must not be doubled",
        )
    }

    @Test
    fun `two adds of the same category name at once become one 409, not a 500`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        alice.post("/api/trips/$tripId/categories", mapOf("name" to "Snacks", "icon" to "cookie", "hue" to 3))

        Mockito.doReturn(false).`when`(categories).keyTakenFor(tripId, "snacks")

        val racing = alice.post(
            "/api/trips/$tripId/categories",
            mapOf("name" to "Snacks", "icon" to "cookie", "hue" to 3),
        )

        assertEquals(HttpStatus.CONFLICT, racing.statusCode, "the race loser must be a 409, not a 500")
    }
}
