package app.ledger.server.category

import app.ledger.server.trip.TripAccess
import org.springframework.dao.DataIntegrityViolationException
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

        val category = CategoryEntity(
            tripId = tripId,
            key = key,
            // Both languages get what was typed. A name somebody chose is not ours to translate,
            // and i18n covers the interface, not the user's own words.
            nameEn = name,
            nameZh = name,
            icon = command.icon.trim(),
            hue = command.hue.toShort(),
            sortOrder = (BUILT_IN_COUNT + categories.countByTripId(tripId) + 1).toShort(),
        )
        val saved = try {
            // Flushed in-transaction so two simultaneous adds of the same name are one 409, not a 500.
            categories.saveAndFlush(category)
        } catch (race: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This trip already has a category like that", race)
        }
        return saved.toView()
    }

    /**
     * A stable key for the partial unique index, folding case and punctuation so "Food" and "food!"
     * collide. `\p{L}\p{N}` keeps letters and digits of *every* script, not just ASCII — "夜宵" is a
     * category name in a bilingual app, so it must slug to "夜宵", not to empty and a 400. The guard
     * in [add] still trips on a name with no letters or digits in any script (e.g. "!!!").
     */
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

        /** Runs of anything that is not a Unicode letter or digit — punctuation, spaces, emoji. */
        val NON_SLUG = Regex("[^\\p{L}\\p{N}]+")
    }
}
