package app.ledger.server.item

import app.ledger.server.auth.LedgerPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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
    @PostMapping("/api/trips/{tripId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable tripId: UUID,
        @Valid @RequestBody command: CreateItem,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): ItemView = items.create(tripId, command, principal.userId)

    @GetMapping("/api/items/{itemId}")
    fun detail(
        @PathVariable itemId: UUID,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): ItemView = items.detail(itemId, principal.userId)

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
