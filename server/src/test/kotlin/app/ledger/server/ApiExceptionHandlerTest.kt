package app.ledger.server

import app.ledger.server.item.ItemEntity
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The exception → status mappings, unit-tested so a broken one cannot slip past a green suite.
 *
 * The @RestControllerAdvice wiring itself is proven over HTTP by the auth and invite tests (their
 * reasons reach the client with the right status). What those cannot cover is the optimistic-lock
 * case: no HTTP request can trigger a stale-version conflict deterministically, so its mapping —
 * the user-facing half of the L8 contract — is pinned here instead.
 */
class ApiExceptionHandlerTest {
    private val handler = ApiExceptionHandler()

    @Test
    fun `a stale-edit conflict maps to 409 with a reason`() {
        val problem = handler.staleEdit(
            ObjectOptimisticLockingFailureException(ItemEntity::class.java, UUID.randomUUID()),
        )

        assertEquals(HttpStatus.CONFLICT.value(), problem.status, "a concurrent edit is a conflict, not a 500")
        assertNotNull(problem.detail, "a 409 must carry a reason the client can show")
    }
}
