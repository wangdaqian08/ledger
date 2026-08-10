package app.ledger.server.item

import app.ledger.server.category.CategoryRepository
import app.ledger.server.payback.toView
import app.ledger.server.trip.TripAccess
import app.ledger.server.trip.TripEntity
import app.ledger.server.trip.TripMemberRepository
import app.ledger.server.trip.TripSnapshot
import app.ledger.server.trip.TripSnapshots
import jakarta.persistence.EntityManager
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * The insert of a client-minted item id lost a race to an identical one. Thrown so the controller
 * can retry the create in a fresh transaction, where the pre-check now finds the winner and answers
 * the retry as an idempotent replay — the whole point of the client minting the id (spec §6).
 */
class ItemIdRace(val id: UUID) : RuntimeException()

@Service
class ItemService(
    private val items: ItemRepository,
    private val shares: ItemShareRepository,
    private val members: TripMemberRepository,
    private val categories: CategoryRepository,
    private val snapshots: TripSnapshots,
    private val access: TripAccess,
    private val entityManager: EntityManager,
) {
    /** Any trip member can record what they spent (spec §5). */
    @Transactional
    fun create(tripId: UUID, command: CreateItem, actor: UUID): CreatedItem {
        val trip = access.visibleTrip(tripId, actor)

        // Checked before anything else is validated: a replay should be answered, not re-argued.
        command.id?.let { id ->
            val existing = items.findById(id).orElse(null)
            if (existing != null) {
                if (existing.tripId != trip.id) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "That id is already in use")
                }
                return CreatedItem(viewOf(existing.id, trip.id, actor), fresh = false)
            }
        }

        validateAgainstRoster(trip, command.payerMemberId, command.sharedBy)
        validateSplit(command.splitRule, command.amountMinor, command.sharedBy)
        requireCategoryAvailable(trip.id, command.categoryId)

        val item = ItemEntity(
            id = command.id ?: UUID.randomUUID(),
            tripId = trip.id,
            title = command.title.trim(),
            categoryId = command.categoryId,
            amountMinor = command.amountMinor,
            splitRule = command.splitRule,
            payerMemberId = command.payerMemberId,
            spentOn = command.spentOn,
            note = command.note?.trim()?.ifEmpty { null },
            createdByUserId = actor,
        )
        try {
            // persist, not save: the id is client-assigned, so repository.save would merge — a
            // SELECT-then-INSERT that quietly turns a duplicate id into an UPDATE. persist always
            // INSERTs, so a lost race meets the unique constraint head-on. Flushed now, inside the
            // transaction, so that surfaces here as something we can catch rather than a commit-time
            // 500. (items.flush routes through the repository proxy, which translates the exception.)
            entityManager.persist(item)
            items.flush()
        } catch (race: DataIntegrityViolationException) {
            // Two creates of the same client-minted id passed the replay check together, and this
            // one lost the insert. A retry, not a duplicate — hand it back to the controller to
            // answer as the replay it is, rather than doubling somebody's dinner.
            command.id?.let { throw ItemIdRace(it) }
            throw race
        }
        writeShares(item, command.sharedBy)

        return CreatedItem(viewOf(item.id, trip.id, actor), fresh = true)
    }

    @Transactional(readOnly = true)
    fun detail(itemId: UUID, actor: UUID): ItemDetailView {
        val item = items.findById(itemId).orElseThrow { noSuchItem() }
        access.visibleTrip(item.tripId, actor)

        val snapshot = snapshots.load(item.tripId)
        val loaded = snapshot.items.first { it.id == item.id }
        return ItemDetailView(
            item = snapshot.toView(loaded, actor),
            paybacks = snapshot.paybacksFor(loaded).map { it.toView() },
        )
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

        val effectiveShares = sharedBy ?: shares.findAllByIdItemIdOrderByPosition(item.id).map {
            ShareInput(it.id.memberId, it.weight, it.exactAmountMinor)
        }
        validateAgainstRoster(trip, payerMemberId, effectiveShares)
        validateSplit(
            rule = command.splitRule ?: item.splitRule,
            amountMinor = command.amountMinor ?: item.amountMinor,
            sharedBy = effectiveShares,
        )
        command.categoryId?.let { requireCategoryAvailable(trip.id, it) }

        command.title?.let {
            val trimmed = it.trim()
            // Create refuses a blank name (@NotBlank); a patch must too, or a correction can leave
            // an expense nameless on every screen. Absent still means unchanged — only a supplied
            // blank is refused.
            if (trimmed.isEmpty()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "an expense has to have a name")
            }
            item.title = trimmed
        }
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
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Only the person who paid, or the trip's creator, can change this expense",
            )
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
                // Zero is allowed — on the bill for the record, owing nothing — matching the engine,
                // the browser's split port and the pinned vectors. Only a negative is nonsense, and
                // somebody has to carry weight or there is nothing to divide by.
                if (sharedBy.any { (it.weight ?: 0) < 0 }) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A weight cannot be negative")
                }
                if (sharedBy.none { (it.weight ?: 0) > 0 }) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "At least one person has to carry some weight",
                    )
                }
                // The engine refuses combinations where amount × weight cannot fit in a Long,
                // because past that point the arithmetic wraps and the shares come out wrong with
                // no error. Caught here so the refusal is a 400 the client can act on.
                val heaviest = sharedBy.maxOf { it.weight ?: 0 }
                if (amountMinor > Long.MAX_VALUE / heaviest) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "That amount and those weights are too large to split exactly",
                    )
                }
            }

            SplitRuleName.EXACT -> {
                if (sharedBy.any { it.exactAmountMinor == null }) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Every person needs an exact amount")
                }
                // Checked per share, not only in sum: a negative here and a padded amount there
                // cancel out, sail past the sum check, and die at the database's CHECK as a bare
                // 500. A share below zero is not a share, it is a payment — and there is a flow
                // for those.
                val negative = sharedBy.firstOrNull { (it.exactAmountMinor ?: 0L) < 0L }
                if (negative != null) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "An exact amount cannot be below zero, got ${negative.exactAmountMinor}",
                    )
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
            sharedBy.mapIndexed { index, share ->
                ItemShareEntity(
                    id = ItemShareId(itemId = item.id, memberId = share.memberId),
                    tripId = item.tripId,
                    position = index.toShort(),
                    weight = if (item.splitRule == SplitRuleName.WEIGHTED) share.weight else null,
                    exactAmountMinor = if (item.splitRule == SplitRuleName.EXACT) share.exactAmountMinor else null,
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
