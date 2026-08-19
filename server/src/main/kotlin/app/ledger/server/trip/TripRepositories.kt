package app.ledger.server.trip

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface TripRepository : JpaRepository<TripEntity, UUID> {
    /**
     * The trips a signed-in person can see, which is exactly the trips they hold a claimed member
     * slot on, minus any the creator has deleted. Ordered newest first, matching GroupsHome.
     *
     * Hidden trips *are* here: hiding is a listing decision the screen makes, not a fact the
     * server withholds. Filtering them out down here would take their balance out of the overall
     * position too, and a hidden debt is still a debt.
     */
    @Query(
        """
        SELECT t FROM TripEntity t
        WHERE t.id IN (SELECT m.tripId FROM TripMemberEntity m WHERE m.userId = :userId)
          AND t.deletedAt IS NULL
        ORDER BY t.createdAt DESC
        """,
    )
    fun findAllForUser(userId: UUID): List<TripEntity>

    /** The Recently deleted section: what this person deleted and can still bring back. */
    @Query(
        """
        SELECT t FROM TripEntity t
        WHERE t.createdByUserId = :userId AND t.deletedAt IS NOT NULL
        ORDER BY t.deletedAt DESC
        """,
    )
    fun findAllDeletedForCreator(userId: UUID): List<TripEntity>

    /** The purge sweep's work list: deleted long enough ago that the restore window has passed. */
    @Query("SELECT t FROM TripEntity t WHERE t.deletedAt IS NOT NULL AND t.deletedAt <= :cutoff")
    fun findAllPurgeable(
        @Param("cutoff") cutoff: Instant,
    ): List<TripEntity>

    /**
     * The purge re-reads each trip under a write lock before destroying it. The work list from
     * [findAllPurgeable] is taken outside any transaction, so a restore may land between the list
     * and the delete; this read is what makes that safe. A restore that already committed shows
     * `deletedAt` back to null and the sweep skips the trip; a restore still in flight blocks on
     * this lock until the delete commits, and its own optimistic flush then 404s as it should.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TripEntity t WHERE t.id = :id")
    fun findForPurge(
        @Param("id") id: UUID,
    ): TripEntity?
}

interface TripMemberRepository : JpaRepository<TripMemberEntity, UUID> {
    /**
     * The trip purge only. A bulk delete that runs against the database immediately, bypassing
     * the persistence context, because the purge needs its deletes to happen in the order it says
     * them — a queued delete that flushes later would meet the member foreign keys out of order.
     */
    @Modifying
    @Query("DELETE FROM TripMemberEntity m WHERE m.tripId = :tripId")
    fun purgeAllForTrip(
        @Param("tripId") tripId: UUID,
    )

    fun findAllByTripIdOrderByCreatedAt(tripId: UUID): List<TripMemberEntity>

    fun findAllByTripIdInOrderByCreatedAt(tripIds: Collection<UUID>): List<TripMemberEntity>

    fun findByTripIdAndUserId(tripId: UUID, userId: UUID): TripMemberEntity?

    fun countByTripId(tripId: UUID): Long

    fun existsByTripIdAndDisplayNameIgnoreCase(tripId: UUID, displayName: String): Boolean
}
