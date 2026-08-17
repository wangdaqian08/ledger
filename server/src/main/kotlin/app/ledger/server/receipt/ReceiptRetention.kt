package app.ledger.server.receipt

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * Deletes receipt images [ReceiptProperties.retention] after their trip ends (spec §3): the
 * window exists so people can check receipts while a finished trip settles up, and once it
 * passes, the images go — object, row and pointer — while the expense keeps every number.
 * Reopening a trip clears `closed_at`, which takes its receipts off this work list.
 *
 * Runs in-process on a daily cron because this deployment is a single always-on instance
 * (spec §4, interim shape). A second instance, or the Cloud Run target, would need a lock or
 * a bucket lifecycle rule before this could move there — the sweep itself is idempotent, so
 * the cost of a double run is only duplicate work, but that is a fact to prove then, not assume.
 */
@Component
class ReceiptRetention(
    private val receipts: ReceiptRepository,
    private val storage: ReceiptStorage,
    private val properties: ReceiptProperties,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(ReceiptRetention::class.java)

    @Scheduled(cron = "0 17 3 * * *")
    fun sweep() {
        purgeExpired()
    }

    /**
     * The object goes first, the row second: a lost object with a lingering row answers 404 and
     * is retried tomorrow, while a lost row with a lingering object would sit in the bucket
     * forever with nothing left pointing at it. One failing receipt is logged and skipped rather
     * than aborting the rest of the sweep.
     *
     * @return how many receipts were removed.
     */
    @Transactional
    fun purgeExpired(): Int {
        val cutoff = clock.instant().minus(properties.retention)
        var removed = 0
        for (receipt in receipts.findAllExpired(cutoff)) {
            try {
                storage.delete(receipt.objectName)
                receipts.delete(receipt)
                removed++
            } catch (failure: RuntimeException) {
                log.warn(
                    "could not remove expired receipt {}; it stays on tomorrow's list",
                    receipt.objectName,
                    failure,
                )
            }
        }
        if (removed > 0) {
            log.info("removed {} receipt image(s) whose trips ended over {} ago", removed, properties.retention)
        }
        return removed
    }
}
