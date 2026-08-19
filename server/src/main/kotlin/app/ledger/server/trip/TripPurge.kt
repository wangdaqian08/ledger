package app.ledger.server.trip

import app.ledger.server.item.ItemRepository
import app.ledger.server.payback.PaybackRepository
import app.ledger.server.receipt.ReceiptRepository
import app.ledger.server.receipt.ReceiptStorage
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock

/**
 * Destroys trips whose restore window has passed (spec §3): deleting is reversible for
 * [TripProperties.restoreWindow], and this is what makes it stop being reversible.
 *
 * Destruction states its order explicitly rather than leaning on `ON DELETE CASCADE`, because the
 * schema deliberately refuses the blind version: the member foreign keys on `item_shares` and
 * `paybacks` do not cascade, so a member can never be deleted out from under a share — which is
 * protection worth keeping, and it means the trip row alone cannot take the lot with it. The
 * order is money movements, then expenses (whose own cascades take shares and receipt rows),
 * then people, then the trip, whose cascade takes what is left (custom categories).
 *
 * Runs on the same daily cron footing as [app.ledger.server.receipt.ReceiptRetention], half an
 * hour later, and carries the same caveat: one always-on instance today, and a second one would
 * need a lock before this could be trusted to run in two places at once.
 */
@Component
class TripPurge(
    private val trips: TripRepository,
    private val receipts: ReceiptRepository,
    private val storage: ReceiptStorage,
    private val paybacks: PaybackRepository,
    private val items: ItemRepository,
    private val members: TripMemberRepository,
    private val properties: TripProperties,
    private val clock: Clock,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(TripPurge::class.java)

    /**
     * One transaction per trip, not one around the sweep. A shared transaction cannot keep its
     * per-trip promise: the first database failure poisons the session, rolls back every trip
     * already purged that night — whose storage objects are gone by then — and the same poisoned
     * trip would abort every later run. Committing each trip alone means a failure costs exactly
     * that trip, and the count this method returns only ever speaks of commits that happened.
     */
    private val perTrip = TransactionTemplate(transactionManager)

    /**
     * Objects before rows, the same rule as the receipt sweep and for the same reason: a lost
     * object with a lingering row is retried tomorrow, while a deleted row pointing at an object
     * nobody can name would leave that image in the bucket forever.
     *
     * @return how many trips were destroyed.
     */
    @Scheduled(cron = "0 47 3 * * *")
    fun purgeDeleted(): Int {
        val cutoff = clock.instant().minus(properties.restoreWindow)
        var removed = 0
        for (trip in trips.findAllPurgeable(cutoff)) {
            try {
                val purged = perTrip.execute {
                    // Re-read under a write lock: the work list above was taken outside any
                    // transaction, so a restore may have landed since. If it did, deletedAt is back
                    // to null and this trip is no longer ours to destroy; skip it. Destroying by
                    // trip_id alone — as this once did — would erase a trip the creator was just
                    // told is back.
                    val fresh = trips.findForPurge(trip.id)
                    if (fresh?.deletedAt == null || fresh.deletedAt!!.isAfter(cutoff)) {
                        return@execute false
                    }
                    receipts.findAllByTripId(trip.id).forEach { storage.delete(it.objectName) }
                    paybacks.purgeAllForTrip(trip.id)
                    items.purgeAllForTrip(trip.id)
                    members.purgeAllForTrip(trip.id)
                    trips.delete(fresh)
                    true
                } ?: false
                if (purged) removed++
            } catch (failure: RuntimeException) {
                log.warn("could not purge deleted trip {}; it stays on tomorrow's list", trip.id, failure)
            }
        }
        if (removed > 0) {
            log.info("purged {} trip(s) deleted over {} ago", removed, properties.restoreWindow)
        }
        return removed
    }
}
