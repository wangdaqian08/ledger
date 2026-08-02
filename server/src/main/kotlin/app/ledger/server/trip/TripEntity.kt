package app.ledger.server.trip

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Foreign keys are plain UUID columns rather than JPA associations throughout this package.
 * Every screen reads a trip and its whole member list together, so there is nothing to lazy-load
 * and no N+1 to walk into; explicit repository calls say what is fetched and when.
 */
@Entity
@Table(name = "trips")
class TripEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var name: String,
    /** A Lucide slug — `plane`, `house`, `coffee`. No emoji, ever (Tally rule). */
    @Column(nullable = false)
    var icon: String,
    /** 1..8, the disc colour on GroupCard. */
    @Column(nullable = false)
    var hue: Short,
    /**
     * `char(3)`, not `varchar`. Hibernate maps a bare String to varchar and `ddl-auto: validate`
     * rejects the mismatch, so the type is stated here rather than by editing V1 — migrations are
     * append-only once merged, even while nothing has deployed them.
     */
    @Column(name = "currency_code", nullable = false, updatable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    val currencyCode: String,
    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    val createdByUserId: UUID,
    @Column(name = "starts_on")
    var startsOn: LocalDate? = null,
    @Column(name = "ends_on")
    var endsOn: LocalDate? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "archived_at")
    var archivedAt: Instant? = null,
)

/**
 * A name on a trip. Deliberately not the same thing as a user: a member exists whether or not
 * anybody ever signs in as them, which is what lets the payer tick off a friend who never joins.
 *
 * [userId] stays null until someone claims this name through the share link.
 */
@Entity
@Table(name = "trip_members")
class TripMemberEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "trip_id", nullable = false, updatable = false)
    val tripId: UUID,
    @Column(name = "display_name", nullable = false)
    var displayName: String,
    /** 1..8, assigned round-robin on join and never changed — the person's colour is their identity. */
    @Column(name = "person_hue", nullable = false, updatable = false)
    val personHue: Short,
    @Column(name = "user_id")
    var userId: UUID? = null,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
