package app.ledger.server.receipt

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith

class LocalReceiptStorageTest : ReceiptStorageContract() {
    @TempDir
    lateinit var dir: Path

    override val storage: ReceiptStorage by lazy { LocalReceiptStorage(ReceiptProperties(localDir = dir)) }

    /**
     * Object names are minted by the server, never taken from a request — but the adapter that
     * turns names into filesystem paths is still the wrong place to rely on that.
     */
    @Test
    fun `an object name cannot climb out of the storage directory`() {
        assertFailsWith<IllegalArgumentException> { storage.fetch("../../etc/hosts") }
        assertFailsWith<IllegalArgumentException> { storage.put("a/../../escape", "image/jpeg", byteArrayOf(1)) }
        assertFailsWith<IllegalArgumentException> { storage.delete("..") }
    }
}
