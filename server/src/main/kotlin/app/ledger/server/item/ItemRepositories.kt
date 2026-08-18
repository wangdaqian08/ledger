package app.ledger.server.item

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ItemRepository : JpaRepository<ItemEntity, UUID> {
    /**
     * The trip purge only — immediate bulk delete, so the sweep controls foreign-key order.
     * The database cascades each item's shares and receipt rows off the back of this.
     */
    @Modifying
    @Query("DELETE FROM ItemEntity i WHERE i.tripId = :tripId")
    fun purgeAllForTrip(
        @Param("tripId") tripId: UUID,
    )

    fun findAllByTripIdOrderBySpentOnDescCreatedAtDesc(tripId: UUID): List<ItemEntity>

    fun findAllByTripIdInOrderBySpentOnDescCreatedAtDesc(tripIds: Collection<UUID>): List<ItemEntity>
}

interface ItemShareRepository : JpaRepository<ItemShareEntity, ItemShareId> {
    // Always ordered by position: these lists reach the engine, whose remainder tie-break is
    // positional. An unordered read would let the database's row order decide who pays a cent.
    fun findAllByTripIdOrderByPosition(tripId: UUID): List<ItemShareEntity>

    fun findAllByTripIdInOrderByPosition(tripIds: Collection<UUID>): List<ItemShareEntity>

    fun findAllByIdItemIdOrderByPosition(itemId: UUID): List<ItemShareEntity>

    /**
     * Editing a people list replaces it wholesale rather than diffing: the list is the statement of
     * who shares the cost, and a replace cannot leave a member behind that the client thought it
     * had removed.
     */
    @Transactional
    fun deleteAllByIdItemId(itemId: UUID)
}
