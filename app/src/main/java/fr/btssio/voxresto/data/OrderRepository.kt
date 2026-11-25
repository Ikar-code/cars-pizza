package fr.btssio.voxresto.data

import androidx.room.withTransaction
import java.util.Calendar
import kotlinx.coroutines.flow.Flow

sealed interface ConfirmResult {
    data object Empty : ConfirmResult
    data class Confirmed(val number: Int, val totalCents: Int) : ConfirmResult
}

/**
 * Point d'entrée unique vers la BDD. Chaque opération qui touche plusieurs tables
 * (ligne + événement + ticket) est exécutée dans une transaction : tout ou rien.
 */
class OrderRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val menuDao = db.menuDao()
    private val ticketDao = db.ticketDao()

    val menu: Flow<List<MenuItem>> = menuDao.observeAll()
    val draft: Flow<TicketWithDetails?> = ticketDao.observeDraft()
    val history: Flow<List<TicketWithDetails>> = ticketDao.observeHistory()

    suspend fun ensureSeeded() {
        if (menuDao.count() == 0) menuDao.insertAll(SeedData.menu)
    }

    suspend fun availableMenu(): List<MenuItem> = menuDao.getAvailable()

    suspend fun currentDraft(): TicketWithDetails? = ticketDao.getDraftWithDetails()

    // ---- Carte -------------------------------------------------------------------------------

    /** @return false si un plat du même nom existe déjà. */
    suspend fun addMenuItem(name: String, category: String, priceCents: Int, aliases: String): Boolean =
        menuDao.insert(
            MenuItem(name = name.trim(), category = category.trim(), priceCents = priceCents, aliases = aliases.trim())
        ) != -1L

    suspend fun setAvailable(itemId: Long, available: Boolean) = menuDao.setAvailable(itemId, available)

    // ---- Commande en cours -------------------------------------------------------------------

    /** @return false si le plat n'existe pas ou n'est plus disponible. */
    suspend fun addItem(itemId: Long, quantity: Int, source: InputSource): Boolean =
        db.withTransaction {
            val item = menuDao.getById(itemId)
            if (item == null || !item.available) return@withTransaction false
            val qty = quantity.coerceIn(1, MAX_QUANTITY)
            val ticketId = ensureDraftId(source)
            val existing = ticketDao.findLine(ticketId, itemId)
            if (existing == null) {
                ticketDao.insertLine(
                    TicketLine(
                        ticketId = ticketId,
                        menuItemId = itemId,
                        name = item.name,
                        unitPriceCents = item.priceCents,
                        quantity = qty,
                    )
                )
            } else {
                ticketDao.updateLine(existing.copy(quantity = (existing.quantity + qty).coerceAtMost(MAX_QUANTITY)))
            }
            logEvent(ticketId, EventType.ITEM_ADDED, "$qty × ${item.name}", source)
            touch(ticketId)
            true
        }

    /**
     * Retire [quantity] unités (toute la ligne si null ou si la quantité dépasse celle commandée).
     * @return le nombre d'unités réellement retirées.
     */
    suspend fun removeItem(itemId: Long, quantity: Int?, source: InputSource): Int =
        db.withTransaction {
            val draft = ticketDao.findDraft() ?: return@withTransaction 0
            val line = ticketDao.findLine(draft.id, itemId) ?: return@withTransaction 0
            val removed = if (quantity == null) line.quantity else quantity.coerceIn(1, line.quantity)
            if (removed >= line.quantity) {
                ticketDao.deleteLine(line)
            } else {
                ticketDao.updateLine(line.copy(quantity = line.quantity - removed))
            }
            logEvent(draft.id, EventType.ITEM_REMOVED, "$removed × ${line.name}", source)
            touch(draft.id)
            removed
        }

    suspend fun confirmDraft(source: InputSource): ConfirmResult =
        db.withTransaction {
            val draft = ticketDao.findDraft() ?: return@withTransaction ConfirmResult.Empty
            val details = ticketDao.getWithDetails(draft.id)
            if (details == null || details.lines.isEmpty()) return@withTransaction ConfirmResult.Empty
            val now = clock()
            val number = (ticketDao.maxNumberSince(startOfDay(now)) ?: 0) + 1
            ticketDao.updateTicket(
                draft.copy(status = TicketStatus.CONFIRMED, number = number, confirmedAt = now, updatedAt = now)
            )
            logEvent(draft.id, EventType.CONFIRMED, "Ticket n°$number validé", source)
            ConfirmResult.Confirmed(number, details.totalCents)
        }

    /** Annule la commande en cours. @return false s'il n'y avait rien à annuler. */
    suspend fun cancelDraft(source: InputSource): Boolean =
        db.withTransaction {
            val draft = ticketDao.findDraft() ?: return@withTransaction false
            val details = ticketDao.getWithDetails(draft.id)
            if (details == null || details.lines.isEmpty()) return@withTransaction false
            ticketDao.updateTicket(draft.copy(status = TicketStatus.CANCELLED, updatedAt = clock()))
            logEvent(draft.id, EventType.CANCELLED, "Commande annulée avant validation", source)
            true
        }

    // ---- Suivi des tickets validés -----------------------------------------------------------

    /** CONFIRMED → READY. */
    suspend fun markReady(ticketId: Long, source: InputSource): Boolean =
        transition(ticketId, TicketStatus.CONFIRMED, TicketStatus.READY, EventType.READY, "Commande prête", source)

    /** CONFIRMED → CANCELLED. */
    suspend fun cancelTicket(ticketId: Long, source: InputSource): Boolean =
        transition(ticketId, TicketStatus.CONFIRMED, TicketStatus.CANCELLED, EventType.CANCELLED, "Ticket annulé", source)

    private suspend fun transition(
        ticketId: Long,
        from: TicketStatus,
        to: TicketStatus,
        eventType: EventType,
        detail: String,
        source: InputSource,
    ): Boolean = db.withTransaction {
        val ticket = ticketDao.getById(ticketId)
        if (ticket == null || ticket.status != from) return@withTransaction false
        ticketDao.updateTicket(ticket.copy(status = to, updatedAt = clock()))
        logEvent(ticketId, eventType, detail, source)
        true
    }

    // ---- Helpers -----------------------------------------------------------------------------

    private suspend fun ensureDraftId(source: InputSource): Long {
        ticketDao.findDraft()?.let { return it.id }
        val now = clock()
        val id = ticketDao.insertTicket(Ticket(createdAt = now, updatedAt = now))
        logEvent(id, EventType.CREATED, "Nouvelle commande", source)
        return id
    }

    private suspend fun logEvent(ticketId: Long, type: EventType, detail: String, source: InputSource) {
        ticketDao.insertEvent(
            TicketEvent(ticketId = ticketId, timestamp = clock(), type = type, detail = detail, source = source)
        )
    }

    private suspend fun touch(ticketId: Long) {
        val ticket = ticketDao.getById(ticketId) ?: return
        ticketDao.updateTicket(ticket.copy(updatedAt = clock()))
    }

    private fun startOfDay(now: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    companion object {
        const val MAX_QUANTITY = 99
    }
}
