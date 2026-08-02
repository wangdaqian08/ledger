package app.ledger.server.item

import app.ledger.server.category.CategoryRepository
import app.ledger.server.trip.TripAccess
import app.ledger.server.trip.TripEntity
import app.ledger.server.trip.TripMemberRepository
import app.ledger.server.trip.TripSnapshot
import app.ledger.server.trip.TripSnapshots
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class ItemService(
    private val items: ItemRepository,
    private val shares: ItemShareRepository,
    private val members: TripMemberRepository,
    private val categories: CategoryRepository,
    private val snapshots: TripSnapshots,
    private val access: TripAccess,
) {
    /** Any trip member can record what they spent (spec §5). */
    @Transactional
    fun create(tripId: UUID, command: CreateItem, actor: UUID): ItemView {
        val trip = access.visibleTrip(tripId, actor)
        validateAgainstRoster(trip, command.payerMemberId, command.sharedBy)
        validateSplit(command.splitRule, command.amountMinor, command.sharedBy)
        requireCategoryAvailable(trip.id, command.categoryId)

        val item = items.save(
            ItemEntity(
                tripId = trip.id,
                title = command.title.trim(),
                categoryId = command.categoryId,
                amountMinor = command.amountMinor,
                splitRule = command.splitRule,
                payerMemberId = command.payerMemberId,
                spentOn = command.spentOn,
                note = command.note?.trim()?.ifEmpty { null },
                createdByUserId = actor,
            ),
        )
        writeShares(item, command.sharedBy)

        return viewOf(item.id, trip.id, actor)
    }

    @Transactional(readOnly = true)
    fun detail(itemId: UUID, actor: UUID): ItemView {
        val item = items.findById(itemId).orElseThrow { noSuchItem() }
        access.visibleTrip(item.tripId, actor)
        return viewOf(item.id, item.tripId, actor)
    }

    /**
     * Item payer plus trip creator (spec §5). This is where the hotel case is fixed: ticking two
     * more people onto an item's list re-divides it for everybody, with no rebalancing pass and no
     * stored total to go stale.
     */
    @Transactional
    fun patch(itemId: UUID, command: PatchItem, actor: UUID): ItemView {
        val item = items.findById(itemId).orElseThrow { noSuchItem() }
        val trip = editableTrip(item, actor)

        val payerMemberId = command.payerMemberId ?: item.payerMemberId
        val sharedBy = command.sharedBy
        if (sharedBy != null && sharedBy.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "an expense has to be shared by somebody")
        }

        val effectiveShares = sharedBy ?: shares.findAllByIdItemId(item.id).map {
            ShareInput(it.id.memberId, it.weight, it.exactAmountMinor)
        }
        validateAgainstRoster(trip, payerMemberId, effectiveShares)
        validateSplit(
            rule = command.splitRule ?: item.splitRule,
            amountMinor = command.amountMinor ?: item.amountMinor,
            sharedBy = effectiveShares,
        )
        command.categoryId?.let { requireCategoryAvailable(trip.id, it) }

        command.title?.let { item.title = it.trim() }
        command.categoryId?.let { item.categoryId = it }
        command.amountMinor?.let { item.amountMinor = it }
        command.splitRule?.let { item.splitRule = it }
        command.spentOn?.let { item.spentOn = it }
        command.note?.let { item.note = it.trim().ifEmpty { null } }
        item.payerMemberId = payerMemberId
        item.updatedAt = Instant.now()

        // Rewritten whenever the list *or* the rule changes: a rule change turns weights into
        // exact amounts or into nothing, and leaving the old inputs behind would feed the engine
        // numbers that no longer mean anything.
        if (sharedBy != null || command.splitRule != null) writeShares(item, effectiveShares)

        return viewOf(item.id, trip.id, actor)
    }

    /** Item payer plus trip creator. Cascades take the people list and any claims with it. */
    @Transactional
    fun delete(itemId: UUID, actor: UUID) {
        val item = items.findById(itemId).orElseThrow { noSuchItem() }
        editableTrip(item, actor)
        items.delete(item)
    }

    private fun editableTrip(item: ItemEntity, actor: UUID): TripEntity {
        val trip = access.visibleTrip(item.tripId, actor)
        val payer = members.findById(item.payerMemberId).orElseThrow { noSuchItem() }
        // The payer fronted the money, so it is theirs to correct; the trip creator can correct
        // anything. Everybody else views and claims.
        if (payer.userId != actor && trip.createdByUserId != actor) {
            throw AccessDeniedException("Only the person who paid, or the trip's creator, can change this expense")
        }
        return trip
    }

    private fun validateAgainstRoster(
        trip: TripEntity,
        payerMemberId: UUID,
        sharedBy: List<ShareInput>,
    ) {
        val onTrip = members.findAllByTripIdOrderByCreatedAt(trip.id).mapTo(mutableSetOf()) { it.id }

        if (payerMemberId !in onTrip) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The payer is not on this trip")
        }
        val listed = sharedBy.map { it.memberId }
        if (listed.size != listed.toSet().size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Somebody appears on this expense twice")
        }
        // The database would refuse this anyway — the foreign keys are trip-scoped — but a
        // constraint violation surfaces as a 500, and this is a request that is merely wrong.
        if (!onTrip.containsAll(listed)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Somebody on this expense is not on the trip")
        }
    }

    private fun validateSplit(rule: SplitRuleName, amountMinor: Long, sharedBy: List<ShareInput>) {
        when (rule) {
            SplitRuleName.EQUAL -> {
                Unit
            }

            SplitRuleName.WEIGHTED -> {
                if (sharedBy.any { (it.weight ?: 0) <= 0 }) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Every weight has to be a positive number")
                }
            }

            SplitRuleName.EXACT -> {
                if (sharedBy.any { it.exactAmountMinor == null }) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Every person needs an exact amount")
                }
                // The engine refuses to split amounts that do not add up, and it is right to.
                // Catching it here makes it a 400 the client can act on rather than a 500.
                val given = sharedBy.sumOf { it.exactAmountMinor ?: 0L }
                if (given != amountMinor) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The exact amounts add up to $given, but the expense is $amountMinor",
                    )
                }
            }
        }
    }

    private fun requireCategoryAvailable(tripId: UUID, categoryId: UUID) {
        val category = categories.findById(categoryId).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "No such category")
        }
        // "Built-in, or this trip's own" is not expressible as a foreign key, so it is checked
        // here — the one trip-scoping rule the database cannot hold on its own.
        if (category.tripId != null && category.tripId != tripId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "That category belongs to another trip")
        }
    }

    private fun writeShares(item: ItemEntity, sharedBy: List<ShareInput>) {
        shares.deleteAllByIdItemId(item.id)
        shares.flush()
        shares.saveAll(
            sharedBy.map {
                ItemShareEntity(
                    id = ItemShareId(itemId = item.id, memberId = it.memberId),
                    tripId = item.tripId,
                    weight = if (item.splitRule == SplitRuleName.WEIGHTED) it.weight else null,
                    exactAmountMinor = if (item.splitRule == SplitRuleName.EXACT) it.exactAmountMinor else null,
                )
            },
        )
    }

    private fun viewOf(itemId: UUID, tripId: UUID, actor: UUID): ItemView {
        // Flushed first so the snapshot reads the writes above rather than the state before them.
        items.flush()
        shares.flush()
        val snapshot = snapshots.load(tripId)
        return snapshot.toView(snapshot.items.first { it.id == itemId }, actor)
    }

    private companion object {
        fun noSuchItem() = ResponseStatusException(HttpStatus.NOT_FOUND, "No such expense")
    }
}

/** Kept next to the snapshot so every view of an item is assembled exactly one way. */
fun TripSnapshot.toView(item: ItemEntity, actor: UUID): ItemView {
    val amounts = sharesOf(item)
    val you = memberFor(actor)?.id
    val inputs = itemShareInputs(item)

    return ItemView(
        id = item.id,
        tripId = item.tripId,
        title = item.title,
        categoryId = item.categoryId,
        amountMinor = item.amountMinor,
        splitRule = item.splitRule,
        payerMemberId = item.payerMemberId,
        spentOn = item.spentOn,
        note = item.note,
        splits = amounts.map { (memberId, amount) ->
            SplitView(
                memberId = memberId,
                amountMinor = amount,
                weight = inputs[memberId]?.weight,
                exactAmountMinor = inputs[memberId]?.exactAmountMinor,
            )
        },
        yourShareMinor = you?.let { amounts[it] } ?: 0L,
        state = stateOf(item).name,
    )
}
