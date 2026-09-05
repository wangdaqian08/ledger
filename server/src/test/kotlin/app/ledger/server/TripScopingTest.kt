package app.ledger.server

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * One trip must not be able to reach into another.
 *
 * This is not a tidiness concern. A member of trip B on trip A's item would be given a share of
 * money they are not part of, and *both* trips' balances would stop summing to zero — the first of
 * the two invariants in AGENTS.md. Every test here asserts that the database refuses, rather than
 * that the service remembers to check, because the service is the thing that will be rewritten.
 */
class TripScopingTest : PostgresTest() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `a member of the item's own trip can be given a share`() {
        // The control for every rejection below. Without it, an item_shares table that refused
        // *everything* would let all of them pass while the app could no longer split a bill.
        val fixture = twoTrips()

        val inserted = jdbc.update(
            "INSERT INTO item_shares (trip_id, item_id, member_id, position) VALUES (?, ?, ?, 0)",
            fixture.tripA,
            fixture.itemOnTripA,
            fixture.memberOnTripA,
        )

        assertEquals(1, inserted)
    }

    @Test
    fun `a member of one trip cannot be given a share of another trip's item`() {
        val fixture = twoTrips()

        assertFailsWith<DataIntegrityViolationException> {
            jdbc.update(
                "INSERT INTO item_shares (trip_id, item_id, member_id, position) VALUES (?, ?, ?, 0)",
                fixture.tripA,
                fixture.itemOnTripA,
                fixture.memberOnTripB,
            )
        }
    }

    @Test
    fun `a share cannot claim to be on a trip its item does not belong to`() {
        // The other way round: name trip B, whose member is genuine, but point at trip A's item.
        val fixture = twoTrips()

        assertFailsWith<DataIntegrityViolationException> {
            jdbc.update(
                "INSERT INTO item_shares (trip_id, item_id, member_id, position) VALUES (?, ?, ?, 0)",
                fixture.tripB,
                fixture.itemOnTripA,
                fixture.memberOnTripB,
            )
        }
    }

    @Test
    fun `an item cannot be paid for by a member of another trip`() {
        val fixture = twoTrips()

        assertFailsWith<DataIntegrityViolationException> {
            insertItem(fixture.tripA, payer = fixture.memberOnTripB, user = fixture.user)
        }
    }

    @Test
    fun `a payback cannot be owed to a member of another trip`() {
        val fixture = twoTrips()

        assertFailsWith<DataIntegrityViolationException> {
            jdbc.update(
                """
                INSERT INTO paybacks (id, trip_id, from_member_id, to_member_id, amount_minor,
                                      paid_on, status, created_by_user_id)
                VALUES (gen_random_uuid(), ?, ?, ?, 1000, current_date, 'PENDING', ?)
                """.trimIndent(),
                fixture.tripA,
                fixture.memberOnTripA,
                fixture.memberOnTripB,
                fixture.user,
            )
        }
    }

    @Test
    fun `a trip-level settlement is still allowed to have no item`() {
        // The composite key on (trip_id, item_id) must not accidentally forbid the Settle-up
        // screen's own rows, which carry no item at all — spec §7a.
        val fixture = twoTrips()
        val second = insertMember(fixture.tripA, "Wendy")

        val inserted = jdbc.update(
            """
            INSERT INTO paybacks (id, trip_id, item_id, from_member_id, to_member_id, amount_minor,
                                  paid_on, status, created_by_user_id)
            VALUES (gen_random_uuid(), ?, NULL, ?, ?, 1000, current_date, 'PENDING', ?)
            """.trimIndent(),
            fixture.tripA,
            fixture.memberOnTripA,
            second,
            fixture.user,
        )

        assertEquals(1, inserted)
    }

    private class TwoTrips(
        val user: UUID,
        val tripA: UUID,
        val tripB: UUID,
        val memberOnTripA: UUID,
        val memberOnTripB: UUID,
        val itemOnTripA: UUID,
    )

    private fun twoTrips(): TwoTrips {
        val user = insertUser()
        val tripA = insertTrip("Ski", user)
        val tripB = insertTrip("Beach", user)
        val memberOnTripA = insertMember(tripA, "Bob")
        val memberOnTripB = insertMember(tripB, "Mallory")
        return TwoTrips(
            user = user,
            tripA = tripA,
            tripB = tripB,
            memberOnTripA = memberOnTripA,
            memberOnTripB = memberOnTripB,
            itemOnTripA = insertItem(tripA, payer = memberOnTripA, user = user),
        )
    }

    private fun insertUser(): UUID {
        // Unique per call: one Postgres serves the whole suite, so rows outlive the test.
        val subject = UUID.randomUUID().toString()
        return jdbc.queryForObject(
            """
            INSERT INTO users (id, provider, subject, email, display_name)
            VALUES (gen_random_uuid(), 'test', ?, ?, 'Test User') RETURNING id
            """.trimIndent(),
            UUID::class.java,
            subject,
            "$subject@ledger.test",
        )!!
    }

    private fun insertTrip(name: String, user: UUID): UUID =
        jdbc.queryForObject(
            """
            INSERT INTO trips (id, name, icon, hue, currency_code, created_by_user_id)
            VALUES (gen_random_uuid(), ?, 'plane', 1, 'AUD', ?) RETURNING id
            """.trimIndent(),
            UUID::class.java,
            name,
            user,
        )!!

    private fun insertMember(trip: UUID, name: String): UUID =
        jdbc.queryForObject(
            """
            INSERT INTO trip_members (id, trip_id, display_name, person_hue)
            VALUES (gen_random_uuid(), ?, ?, 1) RETURNING id
            """.trimIndent(),
            UUID::class.java,
            trip,
            name,
        )!!

    private fun insertItem(trip: UUID, payer: UUID, user: UUID): UUID =
        jdbc.queryForObject(
            """
            INSERT INTO items (id, trip_id, title, category_id, amount_minor, split_rule,
                               payer_member_id, spent_on, created_by_user_id)
            VALUES (gen_random_uuid(), ?, 'Hotel', ?, 100000, 'EQUAL', ?, current_date, ?)
            RETURNING id
            """.trimIndent(),
            UUID::class.java,
            trip,
            FOOD_CATEGORY,
            payer,
            user,
        )!!

    private companion object {
        /** A built-in seeded by V1__init.sql. */
        val FOOD_CATEGORY: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }
}
