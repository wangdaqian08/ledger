package app.ledger.server

import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Settle-up screen (§7a): bilateral rows, Pay as a request rather than an act, and Remind.
 *
 * The identity that matters is the second of the two invariants — a person's rows against everyone
 * else must sum to minus their overall position. If that breaks, the screen silently lies about
 * who owes what, which is the one failure the app cannot survive.
 */
class SettlementApiTest : ApiTest() {
    @Test
    fun `each row is your position with one person, and the rows sum to minus your net`() {
        val trip = threeWayTrip()

        val settlement = trip.alice.get("/api/trips/${trip.id}/settlement").json()

        val rows = settlement["rows"]
        assertEquals(2, rows.size(), "one row per other person, not a minimised transfer set")
        // The identity from §7a, over HTTP. It is a property test in the engine; this is the
        // assurance that the adapter did not lose it on the way out.
        assertEquals(
            -settlement["yourNetMinor"].asLong(),
            rows.sumOf { it["owedMinor"].asLong() },
            "the settle-up column does not agree with the hero figure",
        )
    }

    @Test
    fun `a row says which way the money goes`() {
        // Alice fronted $90 for three, so each of the others owes her $30.
        val trip = threeWayTrip()

        val alicesRows = trip.alice.rows(trip.id)
        val bobsRows = trip.bob.rows(trip.id)

        assertEquals(-3_000, alicesRows.getValue(trip.bobMember), "negative means they owe you")
        assertEquals(3_000, bobsRows.getValue(trip.aliceMember), "positive means you owe them")
    }

    @Test
    fun `tapping Pay settles nothing on its own`() {
        val trip = threeWayTrip()

        val paid = trip.bob.post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
        )

        assertEquals(HttpStatus.CREATED, paid.statusCode)
        assertEquals("PENDING", paid.json()["status"].asText())
        assertTrue(paid.json()["itemId"].isNull, "a settlement belongs to no single bill")
        // Still owed, because Alice has not agreed. This is why display-only settling was dropped.
        assertEquals(3_000, trip.bob.rows(trip.id).getValue(trip.aliceMember))
        assertFalse(
            trip.bob
                .get("/api/trips/${trip.id}/settlement")
                .json()["allSquare"]
                .asBoolean(),
        )
    }

    @Test
    fun `the row shows the claim waiting on the other person`() {
        val trip = threeWayTrip()
        trip.bob.post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
        )

        val bobsRow = trip.bob.rowFor(trip.id, trip.aliceMember)
        val alicesRow = trip.alice.rowFor(trip.id, trip.bobMember)

        // Both ends can see it: Bob's "sent for confirmation", Alice's "waiting on you".
        assertEquals(1, bobsRow["pending"].size())
        assertEquals(1, alicesRow["pending"].size())
    }

    @Test
    fun `approving the settlement is what clears the row`() {
        val trip = threeWayTrip()
        val settlement = trip.bob
            .post(
                "/api/trips/${trip.id}/settlements",
                mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
            ).id()

        trip.alice.post("/api/paybacks/$settlement/approve", emptyMap<String, String>())

        assertEquals(0, trip.bob.rows(trip.id).getValue(trip.aliceMember))
        assertEquals(0, trip.alice.rows(trip.id).getValue(trip.bobMember))
        // Carol still owes, so the trip is not square yet — all-square is derived, never a button.
        assertFalse(
            trip.alice
                .get("/api/trips/${trip.id}/settlement")
                .json()["allSquare"]
                .asBoolean(),
        )
    }

    @Test
    fun `you are square once your own net is zero`() {
        val trip = threeWayTrip()

        listOf(trip.bob, trip.carol).forEach { debtor ->
            val settlement = debtor
                .post(
                    "/api/trips/${trip.id}/settlements",
                    mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
                ).id()
            trip.alice.post("/api/paybacks/$settlement/approve", emptyMap<String, String>())
        }

        assertTrue(
            trip.alice
                .get("/api/trips/${trip.id}/settlement")
                .json()["allSquare"]
                .asBoolean(),
        )
        assertEquals(
            0,
            trip.alice
                .get("/api/trips/${trip.id}")
                .json()["yourNetMinor"]
                .asLong(),
        )
    }

    @Test
    fun `an item repayment and a settlement between the same two people do not both subtract`() {
        // One record type, one state machine (§5). This is the double-subtraction that having a
        // separate settlements table would have invited.
        val trip = threeWayTrip()
        val repayment = trip.bob
            .post(
                "/api/items/${trip.item}/paybacks",
                mapOf("fromMemberId" to trip.bobMember.toString(), "amountMinor" to 1_000, "paidOn" to "2026-08-02"),
            ).id()
        trip.alice.post("/api/paybacks/$repayment/approve", emptyMap<String, String>())

        val settlement = trip.bob
            .post(
                "/api/trips/${trip.id}/settlements",
                mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 2_000),
            ).id()
        trip.alice.post("/api/paybacks/$settlement/approve", emptyMap<String, String>())

        assertEquals(0, trip.bob.rows(trip.id).getValue(trip.aliceMember), "3000 owed, 3000 paid, in two pieces")
    }

    @Test
    fun `undoing an approved settlement un-settles the trip`() {
        val trip = threeWayTrip()
        val settlement = trip.bob
            .post(
                "/api/trips/${trip.id}/settlements",
                mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
            ).id()
        trip.alice.post("/api/paybacks/$settlement/approve", emptyMap<String, String>())

        trip.bob.post("/api/paybacks/$settlement/undo", emptyMap<String, String>())

        assertEquals(3_000, trip.bob.rows(trip.id).getValue(trip.aliceMember))
    }

    @Test
    fun `paying more than you owe is allowed`() {
        // Rounding a debt up, or covering something not yet entered. Refusing would be the app
        // telling somebody they are wrong about their own money.
        val trip = threeWayTrip()

        val paid = trip.bob.post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 5_000),
        )

        assertEquals(HttpStatus.CREATED, paid.statusCode)
    }

    @Test
    fun `you cannot settle with yourself, or with somebody not on the trip`() {
        val trip = threeWayTrip()
        val elsewhere = threeWayTrip()

        assertEquals(
            HttpStatus.BAD_REQUEST,
            trip.bob
                .post(
                    "/api/trips/${trip.id}/settlements",
                    mapOf("toMemberId" to trip.bobMember.toString(), "amountMinor" to 100),
                ).statusCode,
        )
        assertEquals(
            HttpStatus.BAD_REQUEST,
            trip.bob
                .post(
                    "/api/trips/${trip.id}/settlements",
                    mapOf("toMemberId" to elsewhere.bobMember.toString(), "amountMinor" to 100),
                ).statusCode,
        )
    }

    @Test
    fun `a reminder only goes to somebody who actually owes you`() {
        val trip = threeWayTrip()

        val nudge = trip.alice.post("/api/trips/${trip.id}/remind", mapOf("memberId" to trip.bobMember.toString()))
        val backwards = trip.bob.post("/api/trips/${trip.id}/remind", mapOf("memberId" to trip.aliceMember.toString()))

        assertEquals(HttpStatus.NO_CONTENT, nudge.statusCode)
        // Bob owes Alice, not the other way round, so there is nothing to nudge her about.
        assertEquals(HttpStatus.BAD_REQUEST, backwards.statusCode)
    }

    @Test
    fun `a reminder writes no payback and moves no balance`() {
        val trip = threeWayTrip()
        val before = trip.alice
            .get("/api/trips/${trip.id}")
            .json()["yourNetMinor"]
            .asLong()

        trip.alice.post("/api/trips/${trip.id}/remind", mapOf("memberId" to trip.bobMember.toString()))

        assertEquals(
            before,
            trip.alice
                .get("/api/trips/${trip.id}")
                .json()["yourNetMinor"]
                .asLong(),
        )
        assertEquals(0, trip.alice.rowFor(trip.id, trip.bobMember)["pending"].size())
    }

    @Test
    fun `the creator cannot wave through a settlement they are the one paying`() {
        // Alice created the trip, so she can approve on the group's behalf — but not a payment she
        // herself is making to somebody who can speak for themselves. Bob is a signed-in member; his
        // is the only agreement that counts here, and the creator hat does not override that.
        val trip = threeWayTrip()
        val claim = trip.alice
            .post(
                "/api/trips/${trip.id}/settlements",
                mapOf("toMemberId" to trip.bobMember.toString(), "amountMinor" to 3_000),
            ).id()

        val selfApprove = trip.alice.post("/api/paybacks/$claim/approve", emptyMap<String, String>())

        assertEquals(HttpStatus.FORBIDDEN, selfApprove.statusCode)
        // The claim is still waiting on Bob, and Bob can still approve it himself.
        val stillPending = trip.bob.rowFor(trip.id, trip.aliceMember)["pending"].single()
        assertEquals(claim.toString(), stillPending["id"].asText())
        assertEquals("PENDING", stillPending["status"].asText())
        assertEquals(
            HttpStatus.OK,
            trip.bob.post("/api/paybacks/$claim/approve", emptyMap<String, String>()).statusCode,
        )
    }

    @Test
    fun `the strip tells each person what they may do with a pending settlement`() {
        val trip = threeWayTrip()
        trip.bob.post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
        )

        // Alice, the person owed, may approve or reject it — and undo it.
        val aliceSees = trip.alice.rowFor(trip.id, trip.bobMember)["pending"].single()
        assertTrue(aliceSees["viewerCanDecide"].asBoolean(), "the recipient may decide")
        assertTrue(aliceSees["viewerCanUndo"].asBoolean())

        // Bob, the one paying, may not wave through his own — only withdraw it.
        val bobSees = trip.bob.rowFor(trip.id, trip.aliceMember)["pending"].single()
        assertFalse(bobSees["viewerCanDecide"].asBoolean(), "the payer cannot decide their own")
        assertTrue(bobSees["viewerCanUndo"].asBoolean(), "but may withdraw it")
    }

    @Test
    fun `an approved settlement stays on the strip as an undoable record for both people`() {
        val trip = threeWayTrip()
        val claim = trip.bob
            .post(
                "/api/trips/${trip.id}/settlements",
                mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
            ).id()
        trip.alice.post("/api/paybacks/$claim/approve", emptyMap<String, String>())

        // It has moved the balance, and it stays visible as a settled, undoable record — so a
        // mistaken confirmation is not a one-way door (§7a).
        val bobRow = trip.bob.rowFor(trip.id, trip.aliceMember)
        assertEquals(0, bobRow["owedMinor"].asLong(), "the balance moved")
        val settled = bobRow["settled"].single()
        assertEquals("APPROVED", settled["status"].asText())
        assertTrue(settled["viewerCanUndo"].asBoolean(), "either party can undo it")
        assertEquals(1, trip.alice.rowFor(trip.id, trip.bobMember)["settled"].size(), "and the recipient sees it too")
    }

    @Test
    fun `a declined settlement comes back to the claimant with the reason`() {
        // A trip-level settlement has no bill sheet, so a decline used to be filtered out of the
        // payload and simply vanish — the claimant never learned it was turned down, or why. It now
        // lands on the row, so the reason reaches the person who made the claim.
        val trip = threeWayTrip()
        val claim = trip.bob
            .post(
                "/api/trips/${trip.id}/settlements",
                mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
            ).id()

        trip.alice.post("/api/paybacks/$claim/reject", mapOf("reason" to "not received"))

        val bobRow = trip.bob.rowFor(trip.id, trip.aliceMember)
        assertEquals(0, bobRow["pending"].size(), "no longer waiting on anyone")
        assertEquals(3_000, bobRow["owedMinor"].asLong(), "the debt is back, undecided")
        val declined = bobRow["rejected"].single()
        assertEquals("REJECTED", declined["status"].asText())
        assertEquals("not received", declined["rejectReason"].asText())

        // The person who declined it is not shown their own decline echoed back at them.
        assertEquals(0, trip.alice.rowFor(trip.id, trip.bobMember)["rejected"].size())
    }

    @Test
    fun `a settlement larger than any real trip is refused`() {
        val trip = threeWayTrip()

        val response = trip.bob.post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 1_000_000_000_001),
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `a stranger sees no settlement at all`() {
        val trip = threeWayTrip()

        assertEquals(HttpStatus.NOT_FOUND, signedIn("Stranger").get("/api/trips/${trip.id}/settlement").statusCode)
    }

    @Test
    fun `the rows sum to minus your net even when you both owe and are owed`() {
        // The existing identity test has a viewer who only owes. This one puts the viewer on both
        // sides at once — owing Alice, owed by Carol — with an approved item repayment and an
        // approved trip-level settlement in the mix, so the §7a identity is exercised across a
        // genuinely mixed-sign row set rather than a one-signed one.
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val aliceMember = alice.yourMemberId(tripId)
        val bobMember = alice.addMember(tripId, "Bob")
        val carolMember = alice.addMember(tripId, "Carol")
        val invite = alice.invite(tripId)
        val bob = signedIn("Bob").also { it.claim(tripId, invite, bobMember) }
        val carol = signedIn("Carol").also { it.claim(tripId, invite, carolMember) }
        val food = alice.builtInCategory(tripId)

        // Alice fronts a bill she shares with Bob → Bob owes Alice $50.
        alice.post(
            "/api/trips/$tripId/items",
            expense("Dinner", 10_000, food, aliceMember, listOf(aliceMember, bobMember)),
        )
        // Bob fronts a bill he shares with Carol → Carol owes Bob $40.
        val bobsItem = bob
            .post(
                "/api/trips/$tripId/items",
                expense("Taxi", 8_000, food, bobMember, listOf(bobMember, carolMember)),
            ).id()

        // Carol repays Bob part of the taxi; Bob, the one owed, approves it.
        val repayment = carol
            .post(
                "/api/items/$bobsItem/paybacks",
                mapOf("fromMemberId" to carolMember.toString(), "amountMinor" to 1_000, "paidOn" to "2026-08-02"),
            ).id()
        bob.post("/api/paybacks/$repayment/approve", emptyMap<String, String>())

        // Bob settles part of what he owes Alice straight from the Settle-up screen; Alice approves.
        val settlement = bob
            .post(
                "/api/trips/$tripId/settlements",
                mapOf("toMemberId" to aliceMember.toString(), "amountMinor" to 1_000),
            ).id()
        alice.post("/api/paybacks/$settlement/approve", emptyMap<String, String>())

        val settleView = bob.get("/api/trips/$tripId/settlement").json()
        val owed = settleView["rows"].map { it["owedMinor"].asLong() }

        assertEquals(
            -settleView["yourNetMinor"].asLong(),
            owed.sum(),
            "the settle-up column does not agree with the hero figure",
        )
        assertTrue(
            owed.any { it > 0 } && owed.any { it < 0 },
            "the viewer should be both owing and owed here, or the identity is trivially one-signed: $owed",
        )
    }

    @Test
    fun `a member who is neither party nor the creator cannot touch a settlement between two others`() {
        // The reviewable and undoable predicates gate this for real, not just the strip: a bystander
        // on the trip gets 403 (not 404 — they can see it) on every verb, even one still PENDING.
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val bobMember = alice.addMember(tripId, "Bob")
        val carolMember = alice.addMember(tripId, "Carol")
        val daveMember = alice.addMember(tripId, "Dave")
        val invite = alice.invite(tripId)
        val bob = signedIn("Bob").also { it.claim(tripId, invite, bobMember) }
        signedIn("Carol").also { it.claim(tripId, invite, carolMember) }
        val dave = signedIn("Dave").also { it.claim(tripId, invite, daveMember) }

        // Bob files a settlement to Carol. Alice is the creator; Dave is on the trip but neither
        // party to it.
        val settlement = bob
            .post(
                "/api/trips/$tripId/settlements",
                mapOf("toMemberId" to carolMember.toString(), "amountMinor" to 3_000),
            ).id()

        assertEquals(
            HttpStatus.FORBIDDEN,
            dave.post("/api/paybacks/$settlement/approve", emptyMap<String, String>()).statusCode,
            "a bystander cannot approve it",
        )
        assertEquals(
            HttpStatus.FORBIDDEN,
            dave.post("/api/paybacks/$settlement/reject", mapOf("reason" to "not my call")).statusCode,
            "nor reject it",
        )
        assertEquals(
            HttpStatus.FORBIDDEN,
            dave.post("/api/paybacks/$settlement/undo", emptyMap<String, String>()).statusCode,
            "nor undo it",
        )
    }


    @Test
    fun `square means your net is zero, even while your rows still cancel each other out`() {
        // The chain: Alice owes Bob $10 and Bob owes Carol $10, so Bob's net is already zero and the
        // fewest-transfers list skips him — Alice pays Carol directly. Once Carol approves, every
        // net is zero yet Alice still "owes Bob" and Carol still "owes Alice" on By person. Those
        // rows are true history and they cancel; insisting every row be zero would leave a trip
        // paid exactly as the app told everyone to pay forever unsquare.
        val chain = chainTrip()
        assertEquals(
            listOf(Triple(chain.aliceMember, chain.carolMember, 1_000L)),
            chain.alice.transfers(chain.id),
            "Bob is square overall from the start, so no transfer passes through him",
        )

        val paid = chain.alice
            .post(
                "/api/trips/${chain.id}/settlements",
                mapOf("toMemberId" to chain.carolMember.toString(), "amountMinor" to 1_000),
            ).id()
        chain.carol.post("/api/paybacks/$paid/approve", emptyMap<String, String>())

        for ((label, viewer) in listOf("Alice" to chain.alice, "Bob" to chain.bob, "Carol" to chain.carol)) {
            val view = viewer.get("/api/trips/${chain.id}/settlement").json()
            val rows = view["rows"].map { it["owedMinor"].asLong() }

            assertEquals(0, view["yourNetMinor"].asLong(), "$label's net")
            assertTrue(view["allSquare"].asBoolean(), "$label is square overall")
            assertEquals(0, view["transfers"].size(), "$label: nothing left to transfer")
            // Invariant 2 holds over the cancelling rows exactly as it does over any other.
            assertEquals(-view["yourNetMinor"].asLong(), rows.sum(), "$label's rows must still sum to minus net")
        }
        assertEquals(1_000, chain.alice.rows(chain.id).getValue(chain.bobMember), "Alice still owes Bob, on paper")
        assertEquals(-1_000, chain.alice.rows(chain.id).getValue(chain.carolMember), "and Carol owes her back")
    }

    @Test
    fun `a reminder is refused once you are square overall, even if somebody's row says they owe you`() {
        // Bob's net is zero from the start: Alice owes him $10 and he owes Carol $10. Nudging Alice
        // would chase money that is, overall, not his — what she owes him he owes on.
        val chain = chainTrip()
        assertEquals(-1_000, chain.bob.rows(chain.id).getValue(chain.aliceMember), "negative: Alice owes Bob on paper")
        assertEquals(
            0,
            chain.bob
                .get("/api/trips/${chain.id}")
                .json()["yourNetMinor"]
                .asLong(),
            "yet Bob is square",
        )

        val nudge = chain.bob.post("/api/trips/${chain.id}/remind", mapOf("memberId" to chain.aliceMember.toString()))

        assertEquals(HttpStatus.BAD_REQUEST, nudge.statusCode)
        assertTrue(nudge.body!!.contains("square"), "the refusal should say why: ${nudge.body}")
    }

    // ---- By minimum transfer ----------------------------------------------------------------

    @Test
    fun `the settlement carries the whole trip's fewest transfers, the same from every seat`() {
        // Alice is owed $30 by each of the other two. Bob and Carol owe the same, so roster order
        // puts Bob first.
        val trip = threeWayTrip()
        val expected = listOf(
            Triple(trip.bobMember, trip.aliceMember, 3_000L),
            Triple(trip.carolMember, trip.aliceMember, 3_000L),
        )

        assertEquals(expected, trip.alice.transfers(trip.id))
        assertEquals(expected, trip.bob.transfers(trip.id), "not filtered to the viewer — the whole trip's list")
        assertEquals(expected, trip.carol.transfers(trip.id))
    }

    @Test
    fun `a settlement waiting for approval leaves the transfers as they were`() {
        val trip = threeWayTrip()
        val before = trip.alice.transfers(trip.id)

        trip.bob.post(
            "/api/trips/${trip.id}/settlements",
            mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
        )

        assertEquals(before, trip.alice.transfers(trip.id), "a claim nobody has agreed to moves nothing")
    }

    @Test
    fun `approving a settlement takes it off the transfers`() {
        val trip = threeWayTrip()
        val paid = trip.bob
            .post(
                "/api/trips/${trip.id}/settlements",
                mapOf("toMemberId" to trip.aliceMember.toString(), "amountMinor" to 3_000),
            ).id()

        trip.alice.post("/api/paybacks/$paid/approve", emptyMap<String, String>())

        assertEquals(listOf(Triple(trip.carolMember, trip.aliceMember, 3_000L)), trip.bob.transfers(trip.id))
    }

    @Test
    fun `following the transfers would square every seat on a mixed trip`() {
        // The HTTP twin of the engine property: from every chair, what the list has you receive less
        // what it has you send is exactly your net — so paying it clears everyone.
        val mixed = mixedTrip()
        val tripId = mixed.id
        val seats = mixed.seats
        val transfers = mixed.alice.transfers(tripId)
        assertTrue(transfers.isNotEmpty(), "a trip this lopsided cannot already be square")
        assertTrue(transfers.size <= seats.size - 1, "at most one fewer transfer than people: $transfers")
        assertTrue(transfers.all { (_, _, amount) -> amount > 0 }, "a transfer of zero or less: $transfers")
        for ((viewer, me) in seats) {
            val view = viewer.get("/api/trips/$tripId/settlement").json()
            val received = transfers.filter { it.second == me }.sumOf { it.third }
            val sent = transfers.filter { it.first == me }.sumOf { it.third }

            assertEquals(transfers, viewer.transfers(tripId), "every seat sees the same list")
            assertEquals(view["yourNetMinor"].asLong(), received - sent, "the transfers would not square $me")
        }
    }

    // ---- How it adds up -----------------------------------------------------------------------

    @Test
    fun `how it adds up ties out on a mixed trip, from every seat`() {
        // The HTTP twin of the engine's breakdown properties: every row is paid − share + settled =
        // net, paid and shares both total the group spend the trip screen shows, and settled and net
        // both total zero — the identities a person checks by hand, which is the table's whole job.
        val mixed = mixedTrip()
        val groupSpend = mixed.alice
            .get("/api/trips/${mixed.id}")
            .json()["groupSpendMinor"]
            .asLong()
        assertEquals(100_001L + 23_457L + 9_999L, groupSpend)

        for ((viewer, me) in mixed.seats) {
            val view = viewer.get("/api/trips/${mixed.id}/settlement").json()
            val rows = view["breakdown"]["rows"].toList()
            val totals = view["breakdown"]["totals"]

            assertEquals(mixed.seats.map { it.second.toString() }, rows.map { it["memberId"].asText() }, "roster order")
            rows.forEach {
                assertEquals(
                    it["netMinor"].asLong(),
                    it["paidMinor"].asLong() - it["shareMinor"].asLong() + it["settledMinor"].asLong(),
                    "$me: the row for ${it["memberId"].asText()} does not tie out",
                )
            }
            assertEquals(0L, rows.sumOf { it["netMinor"].asLong() }, "$me: balances must sum to zero")
            assertEquals(0L, rows.sumOf { it["settledMinor"].asLong() }, "$me: every cent settled was received")
            assertEquals(groupSpend, rows.sumOf { it["paidMinor"].asLong() }, "$me: paid is the group spend")
            assertEquals(groupSpend, rows.sumOf { it["shareMinor"].asLong() }, "$me: shares are the group spend")

            // The totals are the server's, so the browser never adds a column up — and they agree.
            assertEquals(groupSpend, totals["paidMinor"].asLong())
            assertEquals(groupSpend, totals["shareMinor"].asLong())
            assertEquals(0L, totals["settledMinor"].asLong())
            assertEquals(0L, totals["netMinor"].asLong())
            assertEquals(view["transfers"].size(), totals["transfersFewest"].asInt(), "$me: the plan's size")
            assertEquals(2 * totals["transfersFewest"].asInt(), rows.sumOf { it["transfersFewest"].asInt() })
            assertEquals(2 * totals["transfersByPerson"].asInt(), rows.sumOf { it["transfersByPerson"].asInt() })

            // Your own row is the hero figure and your own By person rows, not a third opinion.
            val yours = rows.single { it["isYou"].asBoolean() }
            assertEquals(me.toString(), yours["memberId"].asText())
            assertEquals(view["yourNetMinor"].asLong(), yours["netMinor"].asLong(), "$me: not the hero figure")
            assertEquals(
                view["rows"].count { it["owedMinor"].asLong() != 0L },
                yours["transfersByPerson"].asInt(),
                "$me: not the number of non-zero By person rows",
            )
        }

        // Only approved money is settled: Dave's approved $50 to Bob counts, Carol's pending $20 does not.
        val settled = mixed.alice
            .get("/api/trips/${mixed.id}/settlement")
            .json()["breakdown"]["rows"]
            .associate { UUID.fromString(it["memberId"].asText()) to it["settledMinor"].asLong() }
        assertEquals(
            mapOf(
                mixed.aliceMember to 0L,
                mixed.bobMember to -5_000L,
                mixed.carolMember to 0L,
                mixed.daveMember to 5_000L,
            ),
            settled,
        )
    }

    // --- helpers -----------------------------------------------------------------------------

    private class Fixture(
        val alice: SessionAwareClient,
        val bob: SessionAwareClient,
        val carol: SessionAwareClient,
        val id: UUID,
        val aliceMember: UUID,
        val bobMember: UUID,
        val carolMember: UUID,
        val item: UUID,
    )

    /** Alice fronts $90 shared three ways, so Bob and Carol owe her $30 each. */
    private fun threeWayTrip(): Fixture {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val aliceMember = UUID.fromString(
            alice
                .get("/api/trips/$tripId")
                .json()["members"]
                .first { it["isYou"].asBoolean() }["id"]
                .asText(),
        )
        val bobMember = alice.addMember(tripId, "Bob")
        val carolMember = alice.addMember(tripId, "Carol")
        val invite = alice.invite(tripId)
        val bob = signedIn("Bob").also { it.claim(tripId, invite, bobMember) }
        val carol = signedIn("Carol").also { it.claim(tripId, invite, carolMember) }

        val item = alice
            .post(
                "/api/trips/$tripId/items",
                expense(
                    "Dinner",
                    9_000,
                    alice.builtInCategory(tripId),
                    aliceMember,
                    listOf(aliceMember, bobMember, carolMember),
                ),
            ).id()

        return Fixture(alice, bob, carol, tripId, aliceMember, bobMember, carolMember, item)
    }

    private class Chain(
        val alice: SessionAwareClient,
        val bob: SessionAwareClient,
        val carol: SessionAwareClient,
        val id: UUID,
        val aliceMember: UUID,
        val bobMember: UUID,
        val carolMember: UUID,
    )

    /** Bob fronts $20 for himself and Alice; Carol fronts $20 for herself and Bob. A→B $10, B→C $10. */
    private fun chainTrip(): Chain {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val aliceMember = alice.yourMemberId(tripId)
        val bobMember = alice.addMember(tripId, "Bob")
        val carolMember = alice.addMember(tripId, "Carol")
        val invite = alice.invite(tripId)
        val bob = signedIn("Bob").also { it.claim(tripId, invite, bobMember) }
        val carol = signedIn("Carol").also { it.claim(tripId, invite, carolMember) }
        val food = alice.builtInCategory(tripId)

        bob.post("/api/trips/$tripId/items", expense("Dinner", 2_000, food, bobMember, listOf(aliceMember, bobMember)))
        carol.post(
            "/api/trips/$tripId/items",
            expense("Taxi", 2_000, food, carolMember, listOf(bobMember, carolMember)),
        )

        return Chain(alice, bob, carol, tripId, aliceMember, bobMember, carolMember)
    }

    private class Mixed(
        val alice: SessionAwareClient,
        val id: UUID,
        val aliceMember: UUID,
        val bobMember: UUID,
        val carolMember: UUID,
        val daveMember: UUID,
        /** Every seat with its own member id, in roster order. */
        val seats: List<Pair<SessionAwareClient, UUID>>,
    )

    /**
     * Four people, several payers, a weighted split, an approved repayment and a pending one, so
     * nothing about it is tidy: Alice's cabin for all four, Bob's groceries for three, Carol's fuel
     * weighted 1:2:3:4; Dave repays Bob $50 (approved) and Carol claims $20 (still pending).
     */
    private fun mixedTrip(): Mixed {
        val alice = signedIn("Alice")
        val tripId = alice.createTrip()
        val aliceMember = alice.yourMemberId(tripId)
        val bobMember = alice.addMember(tripId, "Bob")
        val carolMember = alice.addMember(tripId, "Carol")
        val daveMember = alice.addMember(tripId, "Dave")
        val invite = alice.invite(tripId)
        val bob = signedIn("Bob").also { it.claim(tripId, invite, bobMember) }
        val carol = signedIn("Carol").also { it.claim(tripId, invite, carolMember) }
        val dave = signedIn("Dave").also { it.claim(tripId, invite, daveMember) }
        val food = alice.builtInCategory(tripId)
        val everyone = listOf(aliceMember, bobMember, carolMember, daveMember)

        alice.post("/api/trips/$tripId/items", expense("Cabin", 100_001, food, aliceMember, everyone))
        val groceries = bob
            .post(
                "/api/trips/$tripId/items",
                expense("Groceries", 23_457, food, bobMember, listOf(bobMember, carolMember, daveMember)),
            ).id()
        carol.post(
            "/api/trips/$tripId/items",
            expense("Fuel", 9_999, food, carolMember, everyone, splitRule = "WEIGHTED").plus(
                "sharedBy" to everyone.mapIndexed { i, id -> mapOf("memberId" to id.toString(), "weight" to i + 1) },
            ),
        )
        val repaid = dave
            .post(
                "/api/items/$groceries/paybacks",
                mapOf("fromMemberId" to daveMember.toString(), "amountMinor" to 5_000, "paidOn" to "2026-08-02"),
            ).id()
        bob.post("/api/paybacks/$repaid/approve", emptyMap<String, String>())
        carol.post(
            "/api/items/$groceries/paybacks",
            mapOf("fromMemberId" to carolMember.toString(), "amountMinor" to 2_000, "paidOn" to "2026-08-02"),
        )

        val seats = listOf(alice to aliceMember, bob to bobMember, carol to carolMember, dave to daveMember)
        return Mixed(alice, tripId, aliceMember, bobMember, carolMember, daveMember, seats)
    }

    /** The settlement's `transfers`, as (from, to, amount) in the order the server sent them. */
    private fun SessionAwareClient.transfers(tripId: UUID): List<Triple<UUID, UUID, Long>> =
        get("/api/trips/$tripId/settlement")
            .json()["transfers"]
            .map {
                Triple(
                    UUID.fromString(it["fromMemberId"].asText()),
                    UUID.fromString(it["toMemberId"].asText()),
                    it["amountMinor"].asLong(),
                )
            }

    private fun SessionAwareClient.rows(tripId: UUID): Map<UUID, Long> =
        get("/api/trips/$tripId/settlement")
            .json()["rows"]
            .associate { UUID.fromString(it["memberId"].asText()) to it["owedMinor"].asLong() }

    private fun SessionAwareClient.rowFor(tripId: UUID, memberId: UUID) =
        get("/api/trips/$tripId/settlement")
            .json()["rows"]
            .first { it["memberId"].asText() == memberId.toString() }
}
