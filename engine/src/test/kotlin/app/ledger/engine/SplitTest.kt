package app.ledger.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class SplitTest {

    private fun members(vararg names: String) = names.map { MemberId(it) }

    @Test
    fun `rotates which members get the spare cents as the salt changes`() {
        // Without rotation the first person in the list eats the extra cent on every
        // single item of the trip. Over twenty items that is visible, and reads as a bug.
        val people = members("amy", "bob", "cara")

        assertEquals(3_334L, splitEqually(10_000, people, salt = 0)[MemberId("amy")])
        assertEquals(3_334L, splitEqually(10_000, people, salt = 1)[MemberId("bob")])
        assertEquals(3_334L, splitEqually(10_000, people, salt = 2)[MemberId("cara")])
        assertEquals(3_334L, splitEqually(10_000, people, salt = 3)[MemberId("amy")])
    }

    @Test
    fun `hands the spare cents to the earliest members so the parts sum exactly`() {
        // S5: $100.00 split three ways is 33.34 + 33.33 + 33.33 — never 33.33 x 3 = 99.99.
        val result = splitEqually(10_000, members("amy", "bob", "cara"))

        assertEquals(
            mapOf(
                MemberId("amy") to 3_334L,
                MemberId("bob") to 3_333L,
                MemberId("cara") to 3_333L,
            ),
            result,
        )
    }

    @Test
    fun `divides evenly when the total has no remainder`() {
        val result = splitEqually(30_000, members("amy", "bob", "cara"))

        assertEquals(
            mapOf(
                MemberId("amy") to 10_000L,
                MemberId("bob") to 10_000L,
                MemberId("cara") to 10_000L,
            ),
            result,
        )
    }
}
