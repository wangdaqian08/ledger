package app.ledger.server

import app.ledger.server.item.ItemRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Optimistic locking on an expense: two edits made against the same starting state cannot both
 * land. Without it whoever saves last silently wins — and because a patch replaces the whole people
 * list wholesale, the loser's list vanishes with no error, which is exactly the stale-number
 * failure the whole design exists to avoid.
 *
 * Deterministic and thread-free: one edit is prepared against a copy loaded up front, a second edit
 * commits underneath it, and the first is then written — which must be refused, not applied.
 */
class OptimisticLockingTest : ApiTest() {
    @Autowired
    private lateinit var items: ItemRepository

    @Autowired
    private lateinit var txManager: PlatformTransactionManager

    @Test
    fun `an edit made against a stale copy of an expense is refused, not silently applied`() {
        val tx = TransactionTemplate(txManager)
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val me = UUID.fromString(
            alice
                .get("/api/trips/$tripId")
                .json()["members"]
                .first { it["isYou"].asBoolean() }["id"]
                .asText(),
        )
        val id = alice
            .post(
                "/api/trips/$tripId/items",
                expense("Hotel", 10_000, alice.builtInCategory(tripId), me, listOf(me)),
            ).id()

        // Two people open the same expense. open-in-view is off, so this read is already detached:
        // a snapshot of the row as it stands now.
        val stale = items.findById(id).get()

        // One of them commits a change first.
        tx.executeWithoutResult { items.findById(id).get().amountMinor = 99_999 }

        // The other now writes the copy they opened before that change landed. It must be refused.
        assertThrows<ObjectOptimisticLockingFailureException> {
            tx.executeWithoutResult {
                stale.amountMinor = 111
                items.saveAndFlush(stale)
            }
        }

        // The first writer's value stands; the stale write did not overwrite it.
        assertEquals(99_999, items.findById(id).get().amountMinor)
    }
}
