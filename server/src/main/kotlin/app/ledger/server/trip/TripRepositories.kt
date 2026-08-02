package app.ledger.server.trip

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface TripRepository : JpaRepository<TripEntity, UUID> {
    /**
     * The trips a signed-in person can see, which is exactly the trips they hold a claimed member
     * slot on. Ordered newest first, matching GroupsHome.
     */
    @Query(
        """
        SELECT t FROM TripEntity t
        WHERE t.id IN (SELECT m.tripId FROM TripMemberEntity m WHERE m.userId = :userId)
        ORDER BY t.createdAt DESC
        """,
    )
    fun findAllForUser(userId: UUID): List<TripEntity>
}

interface TripMemberRepository : JpaRepository<TripMemberEntity, UUID> {
    fun findAllByTripIdOrderByCreatedAt(tripId: UUID): List<TripMemberEntity>

    fun findAllByTripIdInOrderByCreatedAt(tripIds: Collection<UUID>): List<TripMemberEntity>

    fun findByTripIdAndUserId(tripId: UUID, userId: UUID): TripMemberEntity?

    fun countByTripId(tripId: UUID): Long

    fun existsByTripIdAndDisplayNameIgnoreCase(tripId: UUID, displayName: String): Boolean
}
