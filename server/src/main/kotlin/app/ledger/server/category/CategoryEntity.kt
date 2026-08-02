package app.ledger.server.category

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

/**
 * [tripId] null means one of the eight built-ins, shared by every trip. Non-null means somebody
 * added it to that trip, and it is visible only there.
 */
@Entity
@Table(name = "categories")
class CategoryEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "trip_id", updatable = false)
    val tripId: UUID? = null,
    @Column(name = "key", nullable = false, updatable = false)
    val key: String,
    @Column(name = "name_en", nullable = false)
    var nameEn: String,
    @Column(name = "name_zh", nullable = false)
    var nameZh: String,
    /** A Lucide slug from the vendored set. No emoji, ever. */
    @Column(nullable = false)
    var icon: String,
    @Column(nullable = false)
    var hue: Short,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Short,
)

interface CategoryRepository : JpaRepository<CategoryEntity, UUID> {
    /** Everything this trip may file an expense under: the built-ins plus its own. */
    @Query(
        """
        SELECT c FROM CategoryEntity c
        WHERE c.tripId IS NULL OR c.tripId = :tripId
        ORDER BY c.tripId NULLS FIRST, c.sortOrder
        """,
    )
    fun findAllAvailableTo(tripId: UUID): List<CategoryEntity>

    fun countByTripId(tripId: UUID): Long

    @Query("SELECT count(c) > 0 FROM CategoryEntity c WHERE c.key = :key AND (c.tripId IS NULL OR c.tripId = :tripId)")
    fun keyTakenFor(tripId: UUID, key: String): Boolean
}
