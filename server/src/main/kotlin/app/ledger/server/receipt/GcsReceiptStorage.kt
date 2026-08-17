package app.ledger.server.receipt

import com.google.cloud.NoCredentials
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Receipt bytes in a Cloud Storage bucket — the production adapter, active on the `gcs-receipts`
 * profile. Credentials are Application Default Credentials (the deploy points
 * GOOGLE_APPLICATION_CREDENTIALS at a service-account key scoped to this one bucket); the bucket
 * name has no default for the same reason the invite secret has none — one invented here would be
 * wrong everywhere it was ever used.
 *
 * Uploads are single-shot [Storage.create] calls, not resumable sessions: receipts are capped at
 * megabytes (application.yaml), one upload is one Class A operation against the free tier's
 * 5,000/month, and single-shot is the path the fake-gcs-server contract test exercises without
 * any external-URL choreography.
 */
@Component
@Profile("gcs-receipts")
class GcsReceiptStorage(properties: ReceiptProperties) : ReceiptStorage {
    private val bucket: String = requireNotNull(properties.gcs.bucket) {
        "ledger.receipts.gcs.bucket must be set when the gcs-receipts profile is active"
    }

    private val storage: Storage = StorageOptions
        .newBuilder()
        .apply {
            properties.gcs.endpoint?.let { emulator ->
                // The test harness: plain HTTP against fake-gcs-server, no auth, a throwaway
                // project id. Never set outside a test, where ADC carries credentials and project.
                setHost(emulator)
                setCredentials(NoCredentials.getInstance())
                setProjectId("fake-gcs")
            }
        }.build()
        .service

    override fun put(objectName: String, contentType: String, bytes: ByteArray) {
        storage.create(BlobInfo.newBuilder(BlobId.of(bucket, objectName)).setContentType(contentType).build(), bytes)
    }

    override fun fetch(objectName: String): ByteArray? = storage.get(BlobId.of(bucket, objectName))?.getContent()

    override fun delete(objectName: String) {
        storage.delete(BlobId.of(bucket, objectName))
    }
}
