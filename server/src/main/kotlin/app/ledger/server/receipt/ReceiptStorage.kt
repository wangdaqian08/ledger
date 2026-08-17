package app.ledger.server.receipt

/**
 * Where receipt image bytes live. The second seam of the application after `identity/`: nothing
 * above this interface knows whether bytes land on a local disk or in a Cloud Storage bucket,
 * and nothing should learn. Which adapter is wired is a profile decision — [LocalReceiptStorage]
 * on `dev`, [GcsReceiptStorage] on `gcs-receipts` — and a profile set with neither refuses to
 * start rather than quietly storing nothing.
 *
 * Whole byte arrays rather than streams, on purpose: uploads are capped well below the point
 * where buffering matters (spring.servlet.multipart in application.yaml), and the callers all
 * need the full body anyway — to hash, to measure, to hand to an HTTP response with a length.
 */
interface ReceiptStorage {
    /** Stores [bytes] under [objectName], replacing any object already there. */
    fun put(objectName: String, contentType: String, bytes: ByteArray)

    /** The stored bytes, or null when no such object exists. */
    fun fetch(objectName: String): ByteArray?

    /** Removes the object. Removing one that is already gone is not an error — deletes retry. */
    fun delete(objectName: String)
}
