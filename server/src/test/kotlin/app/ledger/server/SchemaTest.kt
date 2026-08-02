package app.ledger.server

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * That this class starts at all is most of the test: the context only comes up if Flyway applied
 * `V1__init.sql` cleanly and Hibernate's `ddl-auto: validate` then agreed that every entity matches
 * the tables it produced. The assertions below cover what starting up cannot.
 */
class SchemaTest : PostgresTest() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `the eight built-in categories are seeded and belong to no trip`() {
        val builtIns = jdbc.queryForList(
            "SELECT key, icon FROM categories WHERE trip_id IS NULL ORDER BY sort_order",
        )

        assertEquals(
            listOf("food", "drinks", "transport", "stay", "groceries", "fun", "home", "other"),
            builtIns.map { it["key"] },
        )
        // Tally's canonical Lucide glyphs. The rule is no emoji, ever — spec §5.
        assertEquals(
            listOf(
                "utensils",
                "beer",
                "car-front",
                "bed-double",
                "shopping-basket",
                "ticket",
                "house",
                "circle-dashed",
            ),
            builtIns.map { it["icon"] },
        )
    }

    /**
     * The one rule in this codebase that no amount of Kotlin can enforce on its own: money is
     * `Long` minor units, so every money column has to be an integer type. A migration that
     * introduced `numeric` or `double precision` would compile, pass every other test, and be
     * wrong. This is the test that would fail.
     */
    @Test
    fun `every money column is bigint`() {
        val moneyColumns = jdbc.queryForList(
            """
            SELECT table_name, column_name, data_type
            FROM information_schema.columns
            WHERE table_schema = 'public' AND column_name LIKE '%_minor'
            ORDER BY table_name, column_name
            """.trimIndent(),
        )

        assertTrue(moneyColumns.isNotEmpty(), "found no *_minor columns at all — has the naming changed?")
        assertEquals(
            emptyList(),
            moneyColumns.filter { it["data_type"] != "bigint" },
            "money columns must be bigint minor units",
        )
    }

    @Test
    fun `a built-in category key cannot be registered twice`() {
        // NULL trip_id means "built-in", and in Postgres NULLs do not collide — so this is guarded
        // by a partial unique index rather than by the table's own constraints.
        //
        // Asserting the specific exception, not merely "it threw": a dropped connection is also a
        // failure, and this test would have gone green on one.
        assertFailsWith<DuplicateKeyException> {
            jdbc.update(
                """
                INSERT INTO categories (id, trip_id, key, name_en, name_zh, icon, hue, sort_order)
                VALUES (gen_random_uuid(), NULL, 'food', 'Food again', '再次', 'utensils', 1, 99)
                """.trimIndent(),
            )
        }
    }
}
