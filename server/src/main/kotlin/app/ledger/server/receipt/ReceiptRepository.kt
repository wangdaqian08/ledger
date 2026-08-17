package app.ledger.server.receipt

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ReceiptRepository : JpaRepository<ReceiptEntity, UUID> {
    fun findAllByTripId(tripId: UUID): List<ReceiptEntity>

    fun findAllByTripIdIn(tripIds: Collection<UUID>): List<ReceiptEntity>

    /** Every receipt whose trip ended on or before [cutoff] — the retention sweep's work list. */
    @Query(
        "select r from ReceiptEntity r where r.tripId in " +
            "(select t.id from TripEntity t where t.closedAt is not null and t.closedAt <= :cutoff)",
    )
    fun findAllExpired(
        @Param("cutoff") cutoff: Instant,
    ): List<ReceiptEntity>
}
