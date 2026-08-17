package app.ledger.server.receipt

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/**
 * What every receipt store must do, asserted once and run against each adapter. The API layer is
 * tested through whichever adapter the dev profile wires, so this contract is what keeps the
 * others honest — an adapter that passes here is substitutable behind [ReceiptStorage].
 */
abstract class ReceiptStorageContract {
    protected abstract val storage: ReceiptStorage

    @Test
    fun `stores bytes and hands back exactly the same bytes`() {
        storage.put("trip-a/item-1/v1", "image/jpeg", byteArrayOf(1, 2, 3, 4))

        assertContentEquals(byteArrayOf(1, 2, 3, 4), storage.fetch("trip-a/item-1/v1"))
    }

    @Test
    fun `an object that was never stored comes back null, not an error`() {
        assertNull(storage.fetch("trip-a/item-1/never-stored"))
    }

    @Test
    fun `storing under a taken name replaces the bytes outright`() {
        storage.put("trip-a/item-2/v1", "image/jpeg", byteArrayOf(1, 1, 1))
        storage.put("trip-a/item-2/v1", "image/png", byteArrayOf(9, 9))

        assertContentEquals(byteArrayOf(9, 9), storage.fetch("trip-a/item-2/v1"))
    }

    @Test
    fun `deleting removes the object, and deleting it again is quiet`() {
        storage.put("trip-a/item-3/v1", "image/jpeg", byteArrayOf(5))

        storage.delete("trip-a/item-3/v1")

        assertNull(storage.fetch("trip-a/item-3/v1"))
        storage.delete("trip-a/item-3/v1")
    }
}
