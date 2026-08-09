package app.ledger.server.category

import app.ledger.server.auth.LedgerPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/trips/{tripId}/categories")
class CategoryController(private val categories: CategoryService) {
    @GetMapping
    fun list(
        @PathVariable tripId: UUID,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): List<CategoryView> = categories.listFor(tripId, principal.userId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @PathVariable tripId: UUID,
        @Valid @RequestBody command: CreateCategory,
        @AuthenticationPrincipal principal: LedgerPrincipal,
    ): CategoryView = categories.add(tripId, command, principal.userId)
}
