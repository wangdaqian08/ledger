package app.ledger.server.trip

import app.ledger.server.receipt.ReceiptRepository
import app.ledger.server.receipt.ReceiptStorage
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Destroys trips whose restore window has passed (spec §3): deleting is reversible for
 * [TripProperties.restoreWindow], and this is what makes it stop being reversible.
 *
 * The trip row goes last on purpose. Everything else — members, expenses, shares, paybacks and
 * receipt rows — hangs off it by `ON DELETE CASCADE`, so one delete takes the lot, and the
 * cascade is the only version of this that cannot leave half a trip behind.
 *
 * Runs on the same daily cron footing as [app.ledger.server.receipt.ReceiptRetention], and
 * carries the same caveat: one always-on instance today, and a second one would need a lock
 * before this could be trusted to run in two places at once.
 */
@Component
class TripPurge(
    private val trips: TripRepository,
    private val receipts: ReceiptRepository,
    private val storage: ReceiptStorage,
    private val properties: TripProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(TripPurge::class.java)

    /**
     * Objects before rows, the same order the receipt sweep uses and for the same reason: a lost
     * object with a lingering row is retried tomorrow, while a deleted row pointing at an object
     * nobody can name would leave that image in the bucket forever. One failing trip is logged
     * and left for the next run rather than aborting the sweep.
     *
     * Half an hour after the receipt sweep, so a trip deleted the same day it ended has already
     * had its images taken by the cheaper, more specific job.
     *
     * @return how many trips were destroyed.
     */
    @Scheduled(cron = "0 47 3 * * *")
    @Transactional
    fun purgeDeleted(): Int {
        val cutoff = clock.instant().minus(properties.restoreWindow)
        var removed = 0
        for (trip in trips.findAllPurgeable(cutoff)) {
            try {
                receipts.findAllByTripId(trip.id).forEach { storage.delete(it.objectName) }
                trips.delete(trip)
                removed++
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
