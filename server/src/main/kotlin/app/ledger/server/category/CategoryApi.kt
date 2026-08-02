package app.ledger.server.category

import app.ledger.server.auth.LedgerPrincipal
import app.ledger.server.trip.TripAccess
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

data class CategoryView(
    val id: UUID,
    val key: String,
    val nameEn: String,
    val nameZh: String,
    val icon: String,
    val hue: Short,
    val builtIn: Boolean,
)

data class CreateCategory(
    @field:NotBlank
    @field:Size(max = 40)
    val name: String,
    /** One of the vendored Lucide slugs. The rule is no emoji, ever. */
    @field:NotBlank
    val icon: String,
    @field:Min(1)
    @field:Max(8)
    val hue: Int,
)

@Service
class CategoryService(
    private val categories: CategoryRepository,
    private val access: TripAccess,
) {
    @Transactional(readOnly = true)
    fun listFor(tripId: UUID, actor: UUID): List<CategoryView> {
        access.visibleTrip(tripId, actor)
        return categories.findAllAvailableTo(tripId).map { it.toView() }
    }

    /** Any trip member, not just the creator (spec §5) — whoever is adding the expense needs it. */
    @Transactional
    fun add(tripId: UUID, command: CreateCategory, actor: UUID): CategoryView {
        access.visibleTrip(tripId, actor)
        access.memberOf(tripId, actor)

        val name = command.name.trim()
        val key = slugify(name)
        if (key.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "That name has no letters or digits in it")
        }
        // Checked against the built-ins too: a trip with two things called "Food" makes the
        // category picker a guess.
        if (categories.keyTakenFor(tripId, key)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This trip already has a category like that")
        }

        return categories
            .save(
                CategoryEntity(
                    tripId = tripId,
                    key = key,
                    // Both languages get what was typed. A name somebody chose is not ours to
                    // translate, and i18n covers the interface, not the user's own words.
                    nameEn = name,
                    nameZh = name,
                    icon = command.icon.trim(),
                    hue = command.hue.toShort(),
                    sortOrder = (BUILT_IN_COUNT + categories.countByTripId(tripId) + 1).toShort(),
                ),
            ).toView()
    }

    private fun slugify(name: String) = name.lowercase().replace(NON_SLUG, "-").trim('-')

    private fun CategoryEntity.toView() = CategoryView(
        id = id,
        key = key,
        nameEn = nameEn,
        nameZh = nameZh,
        icon = icon,
        hue = hue,
        builtIn = tripId == null,
    )

    private companion object {
        const val BUILT_IN_COUNT = 8
        val NON_SLUG = Regex("[^a-z0-9]+")
    }
}

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
