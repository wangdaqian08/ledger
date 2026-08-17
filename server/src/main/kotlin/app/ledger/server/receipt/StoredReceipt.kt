package app.ledger.server.receipt

/** A receipt as the GET endpoint serves it: the bytes, and the content type they arrived with. */
class StoredReceipt(val contentType: String, val bytes: ByteArray)
