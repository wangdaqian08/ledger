package app.ledger.server.category

import app.ledger.server.trip.TripAccess
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

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
