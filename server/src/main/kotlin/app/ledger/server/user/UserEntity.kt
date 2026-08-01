package app.ledger.server.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A signed-in person. Distinct from `trip_members`, which is a *name on a trip* and exists whether
 * or not anyone ever signs in to claim it (spec §4, claim flow).
 */
@Entity
@Table(name = "users")
class UserEntity(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, updatable = false)
    val provider: String,
    @Column(nullable = false, updatable = false)
    val subject: String,
    // Mutable because the identity provider is the authority on these, not us: a user who renames
    // themselves or changes their photo with Google should see that reflected on the next sign-in.
    @Column(nullable = false)
    var email: String,
    @Column(name = "display_name", nullable = false)
    var displayName: String,
    @Column(name = "photo_url")
    var photoUrl: String?,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
