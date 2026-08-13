package app.ledger.server.export

import app.ledger.server.auth.LedgerPrincipal
import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.nio.charset.StandardCharsets
import java.util.UUID

@RestController
class ExportController(
    private val export: ExportService,
) {
    /** A download, not an API shape: the browser is handed a file the trip's members can keep. */
    @GetMapping("/api/trips/{tripId}/expenses.csv")
    fun expensesCsv(
        @PathVariable tripId: UUID,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): ResponseEntity<ByteArray> {
        val file = export.expensesCsv(tripId, principal.userId)
        return ResponseEntity
            .ok()
            .contentType(MediaType("text", "csv", StandardCharsets.UTF_8))
            .headers { headers ->
                headers.contentDisposition = ContentDisposition
                    .attachment()
                    // filename* per RFC 6266, so a 中文 trip name survives into the saved file.
                    .filename(file.filename, StandardCharsets.UTF_8)
                    .build()
            }.body(file.csv.toByteArray(StandardCharsets.UTF_8))
    }
}
