package app.ledger.server.receipt

import app.ledger.server.auth.LedgerPrincipal
import app.ledger.server.item.ItemView
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
class ReceiptController(private val receipts: ReceiptService) {
    /** 200 for a first upload and a replace alike — the answer either way is the updated item. */
    @PostMapping("/api/items/{itemId}/receipt")
    fun attach(
        @PathVariable itemId: UUID,
        @RequestPart("file") file: MultipartFile,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): ItemView = receipts.attach(itemId, file.contentType, file.bytes, principal.userId)

    /**
     * The image itself, same-origin and session-authenticated — an `<img>` src, not an API the
     * SPA fetches. The URL carries `?v=<version>` and the answer says `immutable`: a browser asks
     * for each receipt once, and a replace mints a new version so the old cache entry is simply
     * never asked for again.
     */
    @GetMapping("/api/items/{itemId}/receipt")
    fun view(
        @PathVariable itemId: UUID,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): ResponseEntity<ByteArray> {
        val stored = receipts.open(itemId, principal.userId)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable")
            .contentType(MediaType.parseMediaType(stored.contentType))
            .body(stored.bytes)
    }

    @DeleteMapping("/api/items/{itemId}/receipt")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @PathVariable itemId: UUID,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ) = receipts.remove(itemId, principal.userId)
}
