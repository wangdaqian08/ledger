package app.ledger.server.item

import app.ledger.server.auth.LedgerPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class ItemController(private val items: ItemService) {
    /**
     * 201 when this request created the expense, 200 when the same client id has been seen before.
     * A retry after a dropped connection is answered rather than doubling somebody's dinner.
     */
    @PostMapping("/api/trips/{tripId}/items")
    fun create(
        @PathVariable tripId: UUID,
        @Valid @RequestBody command: CreateItem,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): ResponseEntity<ItemView> {
        val created = items.create(tripId, command, principal.userId)
        return ResponseEntity.status(if (created.fresh) HttpStatus.CREATED else HttpStatus.OK).body(created.item)
    }

    @GetMapping("/api/items/{itemId}")
    fun detail(
        @PathVariable itemId: UUID,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): ItemDetailView = items.detail(itemId, principal.userId)

    /** Where the people list gets fixed — see [ItemService.patch]. */
    @PatchMapping("/api/items/{itemId}")
    fun patch(
        @PathVariable itemId: UUID,
        @Valid @RequestBody command: PatchItem,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): ItemView = items.patch(itemId, command, principal.userId)

    @DeleteMapping("/api/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable itemId: UUID,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ) = items.delete(itemId, principal.userId)
}
