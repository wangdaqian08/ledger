package app.ledger.server

import app.ledger.server.settlement.FamilyInput
import app.ledger.server.settlement.PreviewFamilies
import app.ledger.server.settlement.SettlementService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.*
import kotlin.test.assertEquals

/**
 * [SettlementService.families] documents itself as pre-validating every condition the engine's
 * `partitionIntoFamilies` would otherwise `require` — including an empty family — so that the
 * engine's own `require()`s are unreachable in practice. The empty-family case was in fact only
 * caught one layer up, by `@NotEmpty` on `FamilyInput.memberIds` via Bean Validation at controller
 * argument resolution, before the service method body ever runs.
 *
 * `SettlementFamilyApiTest`'s "an empty explicit family is refused" cannot catch this: it goes
 * through the real `POST /api/trips/{id}/families` endpoint, where `@Valid` intercepts an empty
 * family before [SettlementService.families] runs at all. This test calls the service directly,
 * the same way [OptimisticLockingTest] reaches past the controller for a case HTTP alone cannot
 * exercise, so a dropped annotation or a non-HTTP caller cannot silently regress to a bare 500.
 */
class SettlementServiceFamiliesDirectTest : ApiTest() {
    @Autowired
    private lateinit var settlementService: SettlementService

    @Test
    fun `an empty family is refused by the service itself, without Bean Validation's help`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val actor = UUID.fromString(alice.get("/api/me").json()["id"].asText())

        val thrown = assertThrows<ResponseStatusException> {
            settlementService.families(tripId, PreviewFamilies(listOf(FamilyInput(emptyList()))), actor)
        }

        assertEquals(HttpStatus.BAD_REQUEST, thrown.statusCode)
        assertEquals("a family can't be empty", thrown.reason)
    }
}
