package app.ledger.server

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryApiTest : ApiTest() {
    @Test
    fun `every trip starts with the eight built-ins and no emoji`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()

        val categories = alice.get("/api/trips/$tripId/categories").json()

        assertEquals(8, categories.size())
        assertTrue(categories.all { it["builtIn"].asBoolean() })
        // Tally's canonical Lucide glyphs. The rule is no emoji, ever (spec §5).
        assertEquals("utensils", categories[0]["icon"].asText())
    }

    @Test
    fun `any trip member can add one, and it belongs to that trip alone`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val bobMember = alice.addMember(tripId, "Bob")
        val bob = signedIn("Bob")
        bob.claim(tripId, alice.invite(tripId), bobMember)

        // Not creator-only: whoever is entering the expense is the one who needs the category.
        val created = bob.post(
            "/api/trips/$tripId/categories",
            mapOf("name" to "Ski passes", "icon" to "ticket", "hue" to 4),
        )

        assertEquals(HttpStatus.CREATED, created.statusCode)
        assertEquals("ski-passes", created.json()["key"].asText())
        assertEquals(false, created.json()["builtIn"].asBoolean())
        // A name somebody typed is not ours to translate — i18n covers the interface, not their words.
        assertEquals("Ski passes", created.json()["nameZh"].asText())

        assertEquals(9, bob.get("/api/trips/$tripId/categories").json().size())
        assertEquals(8, signedIn("Elsewhere").let { it.get("/api/trips/${it.createTrip()}/categories") }.json().size())
    }

    @Test
    fun `a category cannot collide with a built-in or with itself`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()

        val clashesWithBuiltIn = alice.post(
            "/api/trips/$tripId/categories",
            mapOf("name" to "Food", "icon" to "utensils", "hue" to 1),
        )
        alice.post("/api/trips/$tripId/categories", mapOf("name" to "Ski passes", "icon" to "ticket", "hue" to 4))
        val clashesWithItself = alice.post(
            "/api/trips/$tripId/categories",
            mapOf("name" to "  ski   passes  ", "icon" to "ticket", "hue" to 5),
        )

        // Two things called Food in one picker is a guess, whichever of them is built in.
        assertEquals(HttpStatus.CONFLICT, clashesWithBuiltIn.statusCode)
        assertEquals(HttpStatus.CONFLICT, clashesWithItself.statusCode)
    }

    @Test
    fun `a category named in Chinese is accepted, and its slug is stable`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()

        // "夜宵" (late-night snack) is an ordinary category name in a bilingual app; the slug must
        // keep its letters, not collapse to empty and a 400.
        val created = alice.post(
            "/api/trips/$tripId/categories",
            mapOf("name" to "夜宵", "icon" to "utensils", "hue" to 2),
        )
        assertEquals(HttpStatus.CREATED, created.statusCode, created.body ?: "")
        assertEquals("夜宵", created.json()["key"].asText())
        assertEquals("夜宵", created.json()["nameZh"].asText())

        // Stable enough to dedupe: a second one collides rather than silently doubling.
        val again = alice.post(
            "/api/trips/$tripId/categories",
            mapOf("name" to "夜宵", "icon" to "utensils", "hue" to 3),
        )
        assertEquals(HttpStatus.CONFLICT, again.statusCode)
    }

    @Test
    fun `a name with nothing nameable in it is refused`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()

        val response = alice.post(
            "/api/trips/$tripId/categories",
            mapOf("name" to "!!!", "icon" to "ticket", "hue" to 4),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `somebody not on the trip sees no categories at all`() {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()

        assertEquals(HttpStatus.NOT_FOUND, signedIn("Stranger").get("/api/trips/$tripId/categories").statusCode)
    }
}
