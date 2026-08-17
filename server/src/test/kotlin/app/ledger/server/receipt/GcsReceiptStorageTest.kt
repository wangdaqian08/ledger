package app.ledger.server.receipt

import com.google.cloud.NoCredentials
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.StorageOptions
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

/**
 * The contract against a real GCS API — fsouza/fake-gcs-server speaks the same REST surface —
 * rather than against Google, because the suite must run offline and cost nothing. Same
 * companion-start shape as PostgresTest, and no `@Testcontainers` annotation for the same reason
 * recorded there: the extension would stop a shared container after the first class.
 *
 * No external-URL choreography: that exists for resumable upload sessions, and
 * [GcsReceiptStorage] deliberately uses single-shot uploads only.
 */
class GcsReceiptStorageTest : ReceiptStorageContract() {
    override val storage: ReceiptStorage = GcsReceiptStorage(properties)

    companion object {
        private const val BUCKET = "ledger-receipts-contract"

        private val container = GenericContainer("fsouza/fake-gcs-server:1.55.1").apply {
            withExposedPorts(4443)
            withCommand("-scheme", "http")
            waitingFor(Wait.forHttp("/_internal/healthcheck").forPort(4443))
            start()
        }

        private val endpoint = "http://${container.host}:${container.getMappedPort(4443)}"

        private val properties = ReceiptProperties(gcs = ReceiptProperties.Gcs(bucket = BUCKET, endpoint = endpoint))

        init {
            // fake-gcs starts empty; a production bucket is created by the deploy, never the app.
            StorageOptions
                .newBuilder()
                .setHost(endpoint)
                .setCredentials(NoCredentials.getInstance())
                .setProjectId("fake-gcs")
                .build()
                .service
                .create(BucketInfo.of(BUCKET))
        }
    }
}
