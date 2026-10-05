package app.ledger.server

import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A PayID per person: stored once on the account, written only by its owner, shown to everybody
 * who shares a trip with them — and flagged for a week after it changes, because under
 * `name-signin` anybody can sign in as anybody and swap one.
 *
 * The app never sends money to it and never validates its format; it is text people copy.
 */
class PayIdApiTest : ApiTest() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `your PayID is saved and shown back to you`() {
        val ann = signedIn("Ann")

        val saved = ann.setPayId("ann@example.com")

        assertEquals(HttpStatus.OK, saved.statusCode)
        assertEquals("ann@example.com", saved.json()["payId"].asText(), "the PUT answers with the new MeView")
        assertEquals("ann@example.com", ann.get("/api/me").json()["payId"].asText())
    }

    @Test
    fun `nobody has a PayID until they set one`() {
        assertTrue(signedIn("Ann").get("/api/me").json()["payId"].isNull)
    }

    @Test
    fun `everyone on the trip sees your PayID, on the trip and on their home list`() {
        val trip = twoPeople()

        trip.bob.setPayId("0412 345 678")

        val bobOnAnnsTrip = trip.ann.memberView(trip.id, trip.bobMember)
        assertEquals("0412 345 678", bobOnAnnsTrip["payId"].asText(), "shown as entered, never reformatted")
        assertTrue(bobOnAnnsTrip["payIdChangedRecently"].asBoolean())

        // The home list is a different load path (every trip at once); it must carry it too.
        val listed = trip.ann
            .get("/api/trips")
            .json()["trips"]
            .first { it["id"].asText() == trip.id.toString() }["members"]
            .first { it["id"].asText() == trip.bobMember.toString() }
        assertEquals("0412 345 678", listed["payId"].asText())
        assertTrue(listed["payIdChangedRecently"].asBoolean())
    }

    @Test
    fun `a seat nobody has claimed has no PayID and nothing recent about it`() {
        val ann = signedIn("Ann")
        val tripId = ann.createTrip()
        ann.setPayId("ann@example.com")
        val carol = ann.addMember(tripId, "Carol")

        val seat = ann.memberView(tripId, carol)

        assertTrue(seat["payId"].isNull, "a placeholder has no account, so it cannot have a PayID")
        assertFalse(seat["payIdChangedRecently"].asBoolean())
    }

    @Test
    fun `adding a seat answers with no PayID, and renaming a claimed one keeps theirs`() {
        val trip = twoPeople()
        trip.bob.setPayId("bob@example.com")

        val added = trip.ann.post("/api/trips/${trip.id}/members", mapOf("displayName" to "Carol")).json()
        val renamed = trip.ann
            .patch("/api/trips/${trip.id}/members/${trip.bobMember}", mapOf("displayName" to "Robert"))
            .json()

        assertTrue(added["payId"].isNull)
        assertFalse(added["payIdChangedRecently"].asBoolean())
        assertEquals("bob@example.com", renamed["payId"].asText())
        assertTrue(renamed["payIdChangedRecently"].asBoolean())
    }

    @Test
    fun `a blank PayID clears it, and so does null`() {
        val ann = signedIn("Ann")
        ann.setPayId("ann@example.com")

        assertTrue(ann.setPayId("   ").json()["payId"].isNull, "blank is clearing, not a PayID of spaces")

        ann.setPayId("ann@example.com")
        assertTrue(ann.put("/api/me/pay-id", mapOf("payId" to null)).json()["payId"].isNull)
        assertTrue(ann.get("/api/me").json()["payId"].isNull)
    }

    @Test
    fun `surrounding whitespace is trimmed off`() {
        val ann = signedIn("Ann")

        assertEquals("ann@example.com", ann.setPayId("  ann@example.com \n").json()["payId"].asText())
    }

    @Test
    fun `a PayID longer than 256 characters is refused, saying how long it was`() {
        val ann = signedIn("Ann")
        ann.setPayId("kept@example.com")

        val response = ann.setPayId("x".repeat(257))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertTrue(response.body!!.contains("256"), "the refusal should name the limit: ${response.body}")
        assertTrue(response.body!!.contains("257"), "and what it was given: ${response.body}")
        assertEquals("kept@example.com", ann.get("/api/me").json()["payId"].asText(), "nothing was written")
    }

    @Test
    fun `the length limit counts the PayID, not the whitespace around it`() {
        val ann = signedIn("Ann")

        val exactly = ann.setPayId("  " + "x".repeat(256) + "  ")

        assertEquals(HttpStatus.OK, exactly.statusCode, "256 characters once trimmed: ${exactly.body}")
        assertEquals(256, exactly.json()["payId"].asText().length)
    }

    @Test
    fun `the length limit counts characters the way the database does, not UTF-16 units`() {
        // 256 characters outside the BMP are 512 UTF-16 units; char_length in the CHECK sees 256.
        val astral = "😀".repeat(256)

        val response = signedIn("Ann").setPayId(astral)

        assertEquals(HttpStatus.OK, response.statusCode, "256 characters is within the limit: ${response.body}")
        assertEquals(astral, response.json()["payId"].asText())
    }

    @Test
    fun `signing in again keeps your PayID and its timestamp`() {
        // The identity provider refreshes name, email and photo on every sign-in. A PayID is ours,
        // not the provider's, and a sign-in that re-stamped it would light the "updated recently"
        // badge every time somebody opened the app.
        val name = "Ann ${UUID.randomUUID().toString().take(8)}"
        val first = signInExactlyAs(name)
        first.setPayId("ann@example.com")
        val userId = first.userId()
        val stampedAt = payIdUpdatedAt(userId)

        val again = signInExactlyAs(name)

        assertEquals("ann@example.com", again.get("/api/me").json()["payId"].asText())
        assertEquals(stampedAt, payIdUpdatedAt(userId), "a sign-in must not touch the PayID columns")
    }

    @Test
    fun `the recent badge lasts a week after a change, then goes`() {
        val trip = twoPeople()
        trip.bob.setPayId("bob@example.com")
        val bobUser = trip.bob.userId()

        ageBy(bobUser, days = 6)
        assertTrue(trip.ann.memberView(trip.id, trip.bobMember)["payIdChangedRecently"].asBoolean(), "6 days is recent")

        ageBy(bobUser, days = 2)
        assertFalse(
            trip.ann.memberView(trip.id, trip.bobMember)["payIdChangedRecently"].asBoolean(),
            "8 days is not",
        )
    }

    @Test
    fun `saving the same PayID again does not count as a change`() {
        // Re-saving an unchanged form must not light the badge — it would teach people to ignore it.
        val trip = twoPeople()
        trip.bob.setPayId("bob@example.com")
        val bobUser = trip.bob.userId()
        ageBy(bobUser, days = 8)
        val stampedAt = payIdUpdatedAt(bobUser)

        trip.bob.setPayId("  bob@example.com  ")

        assertEquals(stampedAt, payIdUpdatedAt(bobUser), "the same PayID once trimmed is no change")
        assertFalse(trip.ann.memberView(trip.id, trip.bobMember)["payIdChangedRecently"].asBoolean())
    }

    @Test
    fun `clearing a PayID is a change too`() {
        val trip = twoPeople()
        trip.bob.setPayId("bob@example.com")
        ageBy(trip.bob.userId(), days = 8)

        trip.bob.setPayId("")

        val bob = trip.ann.memberView(trip.id, trip.bobMember)
        assertTrue(bob["payId"].isNull)
        assertTrue(bob["payIdChangedRecently"].asBoolean(), "a PayID that vanished is worth a second look too")
    }

    @Test
    fun `setting a PayID needs a signed-in person`() {
        val anonymous = SessionAwareClient("http://localhost:$port")
        anonymous.get("/api/me") // the CSRF cookie, so the refusal is about who, not about the token

        val response = anonymous.put("/api/me/pay-id", mapOf("payId" to "mallory@example.com"))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `a stranger cannot read anybody's PayID off a trip they are not on`() {
        val trip = twoPeople()
        trip.bob.setPayId("bob@example.com")

        val stranger = signedIn("Stranger")

        assertEquals(HttpStatus.NOT_FOUND, stranger.get("/api/trips/${trip.id}").statusCode)
        assertFalse(stranger.get("/api/trips").body!!.contains("bob@example.com"))
    }

    // --- helpers -----------------------------------------------------------------------------

    private class TwoPeople(val ann: SessionAwareClient, val bob: SessionAwareClient, val id: UUID, val bobMember: UUID)

    /** Ann's trip, with Bob signed in and on it. */
    private fun twoPeople(): TwoPeople {
        val ann = signedIn("Ann")
        val tripId = ann.createTrip()
        val bobMember = ann.addMember(tripId, "Bob")
        val bob = signedIn("Bob").also { it.claim(tripId, ann.invite(tripId), bobMember) }
        return TwoPeople(ann, bob, tripId, bobMember)
    }

    /** [ApiTest.signedIn] makes every name unique; signing in *again* as one person needs the exact name. */
    private fun signInExactlyAs(name: String): SessionAwareClient {
        val client = SessionAwareClient("http://localhost:$port")
        client.get("/api/me")
        val response = client.post("/api/auth/session", mapOf("idToken" to name))
        check(response.statusCode == HttpStatus.OK) { "could not sign in as $name: ${response.statusCode}" }
        return client
    }

    private fun SessionAwareClient.setPayId(payId: String) = put("/api/me/pay-id", mapOf("payId" to payId))

    private fun SessionAwareClient.userId(): UUID = UUID.fromString(get("/api/me").json()["id"].asText())

    private fun SessionAwareClient.memberView(tripId: UUID, memberId: UUID): JsonNode =
        get("/api/trips/$tripId").json()["members"].first { it["id"].asText() == memberId.toString() }

    private fun payIdUpdatedAt(userId: UUID): Timestamp {
        val stamped = jdbc.queryForObject(
            "SELECT pay_id_updated_at FROM users WHERE id = ?",
            Timestamp::class.java,
            userId,
        )
        assertNotNull(stamped, "no pay_id_updated_at for $userId")
        return stamped
    }

    /** Moves the last change back in time, as if [days] had passed since. */
    private fun ageBy(userId: UUID, days: Int) {
        jdbc.update(
            "UPDATE users SET pay_id_updated_at = pay_id_updated_at - make_interval(days => ?) WHERE id = ?",
            days,
            userId,
        )
    }
}
