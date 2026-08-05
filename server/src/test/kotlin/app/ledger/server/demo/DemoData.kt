package app.ledger.server.demo

import app.ledger.server.item.ItemEntity
import app.ledger.server.item.ItemRepository
import app.ledger.server.item.ItemShareEntity
import app.ledger.server.item.ItemShareId
import app.ledger.server.item.ItemShareRepository
import app.ledger.server.item.SplitRuleName
import app.ledger.server.payback.PaybackEntity
import app.ledger.server.payback.PaybackRepository
import app.ledger.server.payback.PaybackStatusName
import app.ledger.server.trip.TripEntity
import app.ledger.server.trip.TripMemberEntity
import app.ledger.server.trip.TripMemberRepository
import app.ledger.server.trip.TripRepository
import app.ledger.server.user.UserEntity
import app.ledger.server.user.UserRepository
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The trip the whole application exists for, as something you can click through.
 *
 * This is spec §10 with the paybacks already approved: thirteen people, two $1,000 hotel bills
 * fronted by Bob, nine having paid $100 towards the deposit and twelve $76.92 towards the balance.
 * Jack is on the trip having paid nothing — and, deliberately, is **not yet on either bill's people
 * list**. Ticking him on is the demonstration.
 *
 * Sign in as any of the names below; the dev identity provider takes a name and nothing else.
 */
@Configuration
@Profile("demo")
class DemoData {
    @Bean
    fun seedDemo(
        users: UserRepository,
        trips: TripRepository,
        members: TripMemberRepository,
        items: ItemRepository,
        shares: ItemShareRepository,
        paybacks: PaybackRepository,
    ) = ApplicationRunner {
        Seeder(users, trips, members, items, shares, paybacks).run()
    }

    private class Seeder(
        private val users: UserRepository,
        private val trips: TripRepository,
        private val members: TripMemberRepository,
        private val items: ItemRepository,
        private val shares: ItemShareRepository,
        private val paybacks: PaybackRepository,
    ) {
        @Transactional
        fun run() {
            // A container is fresh every run, but a demo that doubles its own data when somebody
            // restarts with a persistent database would be worse than useless.
            if (trips.count() > 0L) return

            val bobUser = signUp("Bob")
            hokkaido(bobUser)
            flat(signUp("Mei"))

            println("Demo data ready. Sign in as Bob, Mei, or any of Friend 1..12 — the dev provider takes any name.")
        }

        /** The hotel case, mid-story: everybody has paid, and then Jack turned up. */
        private fun hokkaido(bob: UserEntity) {
            val trip = trips.save(
                TripEntity(
                    name = "Hokkaido",
                    icon = "plane",
                    hue = 6,
                    currencyCode = "AUD",
                    createdByUserId = bob.id,
                    startsOn = LocalDate.of(2026, 7, 20),
                    endsOn = LocalDate.of(2026, 7, 28),
                ),
            )

            val bobMember = join(trip, "Bob", hue = 1, userId = bob.id)
            val friends = (1..12).map { join(trip, "Friend $it", hue = (it % 8) + 1) }
            val jack = join(trip, "Jack", hue = 5)

            // Fixed ids so the rounding is the same every time the demo is opened. The id is the
            // split's salt, so a random one would move a cent between people between runs.
            val deposit = bill(trip, "Hotel deposit", DEPOSIT_ID, bobMember, listOf(bobMember) + friends)
            val balance = bill(trip, "Hotel balance", BALANCE_ID, bobMember, listOf(bobMember) + friends)

            friends.take(9).forEach { repay(trip, deposit, it, bobMember, 10_000) }
            friends.forEach { repay(trip, balance, it, bobMember, 7_692) }

            // Jack is on the trip and on neither bill. Adding a member changes no existing item
            // (§3, roster changes are manual) — which is exactly the state the demo wants to open in.
            check(jack.id !in shares.findAllByIdItemId(deposit.id).map { it.id.memberId })
        }

        /** A small second trip, so the home screen has more than one card and one that is square. */
        private fun flat(mei: UserEntity) {
            val trip = trips.save(
                TripEntity(name = "Flat", icon = "house", hue = 4, currencyCode = "AUD", createdByUserId = mei.id),
            )
            val meiMember = join(trip, "Mei", hue = 3, userId = mei.id)
            val sam = join(trip, "Sam", hue = 4)

            val dinner =
                bill(trip, "Sunday roast", FLAT_ITEM_ID, meiMember, listOf(meiMember, sam), amountMinor = 6_250)
            repay(trip, dinner, sam, meiMember, 3_125)
        }

        private fun signUp(name: String) = users.save(
            UserEntity(
                provider = "mock",
                subject = name.lowercase(),
                email = "${name.lowercase()}@ledger.test",
                displayName = name,
                photoUrl = null,
            ),
        )

        private fun join(trip: TripEntity, name: String, hue: Int, userId: UUID? = null) = members.save(
            TripMemberEntity(
                tripId = trip.id,
                displayName = name,
                personHue = hue.toShort(),
                userId = userId,
            ),
        )

        private fun bill(
            trip: TripEntity,
            title: String,
            id: UUID,
            payer: TripMemberEntity,
            sharedBy: List<TripMemberEntity>,
            amountMinor: Long = 100_000,
        ): ItemEntity {
            val item = items.save(
                ItemEntity(
                    id = id,
                    tripId = trip.id,
                    title = title,
                    categoryId = if (amountMinor == 100_000L) STAY_CATEGORY else FOOD_CATEGORY,
                    amountMinor = amountMinor,
                    splitRule = SplitRuleName.EQUAL,
                    payerMemberId = payer.id,
                    spentOn = LocalDate.of(2026, 7, 20),
                    createdByUserId = trip.createdByUserId,
                ),
            )
            shares.saveAll(
                sharedBy.map { ItemShareEntity(ItemShareId(item.id, it.id), tripId = trip.id) },
            )
            return item
        }

        /** Recorded by the person owed, so it is already agreed — as in "approve them all as Bob". */
        private fun repay(
            trip: TripEntity,
            item: ItemEntity,
            from: TripMemberEntity,
            to: TripMemberEntity,
            amountMinor: Long,
        ) = paybacks.save(
            PaybackEntity(
                tripId = trip.id,
                itemId = item.id,
                fromMemberId = from.id,
                toMemberId = to.id,
                amountMinor = amountMinor,
                paidOn = LocalDate.of(2026, 7, 21),
                status = PaybackStatusName.APPROVED,
                createdByUserId = trip.createdByUserId,
                reviewedByUserId = trip.createdByUserId,
                reviewedAt = Instant.now(),
            ),
        )

        private companion object {
            /** Seeded by V1__init.sql. */
            val STAY_CATEGORY: UUID = UUID.fromString("00000000-0000-0000-0000-000000000004")
            val FOOD_CATEGORY: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

            val DEPOSIT_ID: UUID = UUID.fromString("dddddddd-0000-4000-8000-000000000001")
            val BALANCE_ID: UUID = UUID.fromString("dddddddd-0000-4000-8000-000000000002")
            val FLAT_ITEM_ID: UUID = UUID.fromString("dddddddd-0000-4000-8000-000000000003")
        }
    }
}
