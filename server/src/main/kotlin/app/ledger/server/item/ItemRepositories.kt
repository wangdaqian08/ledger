package app.ledger.server.item

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface ItemRepository : JpaRepository<ItemEntity, UUID> {
    fun findAllByTripIdOrderBySpentOnDescCreatedAtDesc(tripId: UUID): List<ItemEntity>

    fun findAllByTripIdInOrderBySpentOnDescCreatedAtDesc(tripIds: Collection<UUID>): List<ItemEntity>
}

interface ItemShareRepository : JpaRepository<ItemShareEntity, ItemShareId> {
    fun findAllByTripId(tripId: UUID): List<ItemShareEntity>

    fun findAllByTripIdIn(tripIds: Collection<UUID>): List<ItemShareEntity>

    fun findAllByIdItemId(itemId: UUID): List<ItemShareEntity>

    /**
     * Editing a people list replaces it wholesale rather than diffing: the list is the statement of
     * who shares the cost, and a replace cannot leave a member behind that the client thought it
     * had removed.
     */
    @Transactional
    fun deleteAllByIdItemId(itemId: UUID)
}
