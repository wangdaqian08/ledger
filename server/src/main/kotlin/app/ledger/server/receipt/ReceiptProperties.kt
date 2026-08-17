package app.ledger.server.receipt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.time.Duration

@ConfigurationProperties("ledger.receipts")
data class ReceiptProperties(
    /**
     * How long receipt images survive a trip's end before the sweep deletes them. Two weeks:
     * long enough to settle up after everyone flies home, short enough that the bucket never
     * accumulates a history nobody asked it to keep.
     */
    val retention: Duration = Duration.ofDays(14),
    /** Where [LocalReceiptStorage] keeps its files. Only the dev profile reads this. */
    val localDir: Path = Path.of(System.getProperty("java.io.tmpdir"), "ledger-receipts"),
    val gcs: Gcs = Gcs(),
) {
    data class Gcs(
        /**
         * The Cloud Storage bucket receipts live in. No default, same reasoning as the invite
         * secret: [GcsReceiptStorage] refuses to start without one rather than inventing a
         * bucket name that would be wrong everywhere.
         */
        val bucket: String? = null,
        /**
         * Emulator endpoint override for tests (fake-gcs-server). Null — the real service —
         * everywhere outside a test.
         */
        val endpoint: String? = null,
    )
}
