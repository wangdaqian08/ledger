package app.ledger.server.receipt

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

/**
 * Receipt bytes as plain files under [ReceiptProperties.localDir] — the dev-profile adapter, so
 * IDE runs and the test suite need no bucket and no network. Content types are not persisted
 * here: the database row is the system of record for those, and this adapter only holds bytes.
 *
 * Object names are minted by the server as `tripId/itemId/uuid`, never taken from a request,
 * but the one place that turns names into filesystem paths still refuses a name that would
 * resolve outside its directory rather than trusting every future caller.
 */
@Component
@Profile("dev")
class LocalReceiptStorage(properties: ReceiptProperties) : ReceiptStorage {
    private val dir: Path = properties.localDir.toAbsolutePath().normalize()

    override fun put(objectName: String, contentType: String, bytes: ByteArray) {
        val target = resolve(objectName)
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
    }

    override fun fetch(objectName: String): ByteArray? {
        val target = resolve(objectName)
        return if (Files.isRegularFile(target)) Files.readAllBytes(target) else null
    }

    override fun delete(objectName: String) {
        Files.deleteIfExists(resolve(objectName))
    }

    private fun resolve(objectName: String): Path {
        val target = dir.resolve(objectName).normalize()
        require(target.startsWith(dir) && target != dir) {
            "receipt object name resolves outside the storage directory: $objectName"
        }
        return target
    }
}
