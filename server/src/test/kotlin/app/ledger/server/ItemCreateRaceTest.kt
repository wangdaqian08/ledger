package app.ledger.server

import app.ledger.server.item.ItemEntity
import app.ledger.server.item.ItemRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

/**
 * The client mints an item's id so a dropped-connection retry is idempotent. Two of those requests
 * can also land at once — the same tap over two flaky networks — and both pass the "have I already
 * seen this id?" check before either inserts. The loser of the insert then meets the unique
 * constraint, and must recover to the winning expense (200): never a 500, never a doubled expense.
 *
 * The race is made deterministic without threads: the idempotency pre-check is blinded exactly once,
 * so the second create walks into the real database constraint the way a genuine concurrent loser
 * would. Everything past that point — the constraint, the rollback, the recovery — is real.
 */
class ItemCreateRaceTest : ApiTest() {
    @MockitoSpyBean
    private lateinit var itemRepository: ItemRepository

    @Test
    fun `a create that loses the id race recovers the winning expense instead of erroring`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val me = UUID.fromString(
            alice
                .get("/api/trips/$tripId")
                .json()["members"]
                .first { it["isYou"].asBoolean() }["id"]
                .asText(),
        )
        val category = alice.builtInCategory(tripId)
        val id = UUID.randomUUID()
        val body = expense("Dinner", 10_000, category, me, listOf(me)) + mapOf("id" to id.toString())

        // The winner: the concurrent create that landed first, committed under this id.
        assertEquals(HttpStatus.CREATED, alice.post("/api/trips/$tripId/items", body).statusCode)

        // Blind the idempotency pre-check exactly once, so the next create skips the fast replay and
        // drives straight into the unique-id insert — precisely what a true race loser does. The
        // retry's pre-check then gets the real winner back (captured here) and answers as a replay.
        val winner = itemRepository.findById(id)
        Mockito
            .doReturn(Optional.empty<ItemEntity>())
            .doReturn(winner)
            .`when`(itemRepository)
            .findById(id)

        val loser = alice.post("/api/trips/$tripId/items", body)

        assertEquals(HttpStatus.OK, loser.statusCode, "the race loser must be answered, not 500'd")
        assertEquals(id.toString(), loser.json()["id"].asText())
        assertEquals(1, alice.get("/api/trips/$tripId").json()["items"].size(), "and the expense must not be doubled")
    }
}
