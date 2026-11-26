package fr.btssio.voxresto.data

import android.database.SQLException
import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ConfirmResult {
    data object Empty : ConfirmResult
    data class Confirmed(val shortNumber: Int, val totalCents: Int) : ConfirmResult
    data class Failed(val reason: String) : ConfirmResult
}

/**
 * Point d'entrée unique vers les données (Room).
 *
 * - La commande en cours de dictée ([draft]) vit en mémoire.
 * - [confirmDraft] l'enregistre en une seule transaction : ticket + lignes + première entrée
 *   d'historique, ou rien du tout. Une commande validée n'est donc jamais perdue ni à moitié écrite.
 */
class OrderRepository(
    private val db: PizzeriaDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val pizzaDao = db.pizzaDao()
    private val ticketDao = db.ticketDao()

    private val draftLock = Mutex()
    private val _draft = MutableStateFlow(DraftOrder())

    val draft: StateFlow<DraftOrder> = _draft.asStateFlow()

    val pizzas: Flow<List<Pizza>> = pizzaDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Tickets en cours + ceux des 7 derniers jours, par ordre d'arrivée. */
    val tickets: Flow<List<TicketWithDetails>> =
        ticketDao.observeRecent().map { list -> list.map { it.toDomain() } }

    suspend fun availablePizzas(): List<Pizza> = pizzaDao.available().map { it.toDomain() }

    fun currentDraft(): DraftOrder = _draft.value

    // ---- Menu (F9) ---------------------------------------------------------------------------

    /** @return false si une pizza porte déjà ce nom. */
    suspend fun createPizza(input: PizzaInput): Boolean = try {
        pizzaDao.insert(
            PizzaEntity(
                name = input.name.trim(),
                description = input.description?.trim()?.takeIf { it.isNotEmpty() },
                price = input.priceCents,
                preparationTime = input.preparationMinutes,
                createdAt = DbTime.format(clock()),
            )
        )
        true
    } catch (e: SQLiteConstraintException) {
        false
    }

    /** @return false si une autre pizza porte déjà ce nom. */
    suspend fun updatePizza(id: Long, input: PizzaInput): Boolean = try {
        pizzaDao.update(
            id = id,
            name = input.name.trim(),
            description = input.description?.trim()?.takeIf { it.isNotEmpty() },
            price = input.priceCents,
            preparationTime = input.preparationMinutes,
            updatedAt = DbTime.format(clock()),
        )
        true
    } catch (e: SQLiteConstraintException) {
        false
    }

    suspend fun setAvailable(id: Long, available: Boolean) {
        pizzaDao.setAvailable(id, available, DbTime.format(clock()))
    }

    /** Les anciens tickets gardent le nom et le prix copiés ; la pizza est aussi retirée du brouillon. */
    suspend fun deletePizza(id: Long) {
        draftLock.withLock {
            pizzaDao.delete(id)
            _draft.update { draft -> draft.copy(lines = draft.lines.filterNot { it.pizzaId == id }) }
        }
    }

    // ---- Commande en cours (F3, F5, F8) ------------------------------------------------------

    /** @return false si la pizza n'existe pas ou n'est plus disponible. */
    suspend fun addToDraft(pizzaId: Long, quantity: Int): Boolean {
        return draftLock.withLock {
            val pizza = pizzaDao.find(pizzaId)
            if (pizza == null || !pizza.isAvailable) return@withLock false
            val qty = quantity.coerceIn(1, MAX_QUANTITY)
            _draft.update { draft ->
                val exists = draft.lines.any { it.pizzaId == pizzaId }
                val lines = if (exists) {
                    draft.lines.map {
                        if (it.pizzaId == pizzaId) it.copy(quantity = (it.quantity + qty).coerceAtMost(MAX_QUANTITY)) else it
                    }
                } else {
                    draft.lines + DraftLine(pizza.id, pizza.name, pizza.price, qty)
                }
                draft.copy(lines = lines)
            }
            true
        }
    }

    /**
     * Retire [quantity] unités (toute la ligne si null ou si la quantité dépasse celle commandée).
     * @return le nombre d'unités réellement retirées.
     */
    suspend fun removeFromDraft(pizzaId: Long, quantity: Int?): Int {
        return draftLock.withLock {
            val line = _draft.value.lines.firstOrNull { it.pizzaId == pizzaId } ?: return@withLock 0
            val removed = if (quantity == null) line.quantity else quantity.coerceIn(1, line.quantity)
            _draft.update { draft ->
                val lines = if (removed >= line.quantity) {
                    draft.lines.filterNot { it.pizzaId == pizzaId }
                } else {
                    draft.lines.map { if (it.pizzaId == pizzaId) it.copy(quantity = it.quantity - removed) else it }
                }
                draft.copy(lines = lines)
            }
            removed
        }
    }

    /** Abandonne la commande en cours (rien n'a été écrit en base). @return false si elle était vide. */
    suspend fun cancelDraft(): Boolean {
        return draftLock.withLock {
            if (_draft.value.isEmpty) return@withLock false
            _draft.value = DraftOrder()
            true
        }
    }

    /** Enregistre la commande en cours : elle devient un ticket « en attente ». */
    suspend fun confirmDraft(): ConfirmResult {
        return draftLock.withLock {
            val lines = _draft.value.lines
            if (lines.isEmpty()) return@withLock ConfirmResult.Empty
            try {
                val ticket = db.withTransaction { insertTicket(lines, clock()) }
                _draft.value = DraftOrder()
                ConfirmResult.Confirmed(ticket.shortNumber, ticket.totalCents)
            } catch (e: SQLException) {
                // La commande reste en cours : l'employé peut réessayer, rien n'est perdu.
                ConfirmResult.Failed(e.message ?: "erreur de base de données")
            }
        }
    }

    private suspend fun insertTicket(lines: List<DraftLine>, now: Long): Ticket {
        // Numéro « yyyyMMdd-NNN » : NNN repart à 001 chaque jour. MAX + 1 reste unique même si la
        // journée contient des tickets annulés.
        val day = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date(now))
        val sequence = ticketDao.lastSequence("$day-%") + 1
        val number = "%s-%03d".format(Locale.ROOT, day, sequence)
        val total = lines.sumOf { it.unitPriceCents * it.quantity }
        val stamp = DbTime.format(now)

        val ticketId = ticketDao.insertTicket(
            TicketEntity(ticketNumber = number, createdAt = stamp, totalPrice = total)
        )
        ticketDao.insertItems(
            lines.map {
                TicketItemEntity(
                    ticketId = ticketId,
                    pizzaId = it.pizzaId,
                    pizzaName = it.name,
                    quantity = it.quantity,
                    unitPrice = it.unitPriceCents,
                    subtotal = it.unitPriceCents * it.quantity,
                )
            }
        )
        ticketDao.insertHistory(
            TicketStatusHistoryEntity(ticketId = ticketId, newStatus = TicketStatus.PENDING.name, changedAt = stamp)
        )
        return Ticket(ticketId, number, now, TicketStatus.PENDING, total)
    }

    // ---- Suivi des tickets (F7) --------------------------------------------------------------

    /** PENDING → PREPARING → COMPLETED. @return false si le ticket est déjà terminé ou annulé. */
    suspend fun advance(ticketId: Long): Boolean = changeStatus(ticketId) { current ->
        when (current) {
            TicketStatus.PENDING -> TicketStatus.PREPARING
            TicketStatus.PREPARING -> TicketStatus.COMPLETED
            else -> null
        }
    }

    /** Annule un ticket en attente ou en préparation. */
    suspend fun cancelTicket(ticketId: Long): Boolean = changeStatus(ticketId) { current ->
        if (current == TicketStatus.PENDING || current == TicketStatus.PREPARING) TicketStatus.CANCELLED else null
    }

    /** Change l'état et écrit la ligne d'historique dans la même transaction. */
    private suspend fun changeStatus(ticketId: Long, next: (TicketStatus) -> TicketStatus?): Boolean =
        db.withTransaction {
            val current = ticketDao.statusOf(ticketId)?.let { TicketStatus.valueOf(it) }
                ?: return@withTransaction false
            val target = next(current) ?: return@withTransaction false
            ticketDao.setStatus(ticketId, target.name)
            ticketDao.insertHistory(
                TicketStatusHistoryEntity(
                    ticketId = ticketId,
                    oldStatus = current.name,
                    newStatus = target.name,
                    changedAt = DbTime.format(clock()),
                )
            )
            true
        }

    companion object {
        const val MAX_QUANTITY = 99
    }
}

// ---- Entités Room → modèles de l'interface -----------------------------------------------------

private fun PizzaEntity.toDomain() = Pizza(
    id = id,
    name = name,
    description = description,
    priceCents = price,
    preparationMinutes = preparationTime,
    isAvailable = isAvailable,
)

private fun TicketWithRelations.toDomain() = TicketWithDetails(
    ticket = Ticket(
        id = ticket.id,
        number = ticket.ticketNumber,
        createdAtMillis = DbTime.parse(ticket.createdAt),
        status = TicketStatus.valueOf(ticket.status),
        totalCents = ticket.totalPrice,
    ),
    // @Relation ne garantit pas l'ordre : on trie ici.
    items = items.sortedBy { it.id }.map {
        TicketItem(
            id = it.id,
            ticketId = it.ticketId,
            pizzaId = it.pizzaId,
            pizzaName = it.pizzaName,
            quantity = it.quantity,
            unitPriceCents = it.unitPrice,
            subtotalCents = it.subtotal,
        )
    },
    history = history.sortedWith(compareBy({ it.changedAt }, { it.id })).map {
        StatusChange(
            id = it.id,
            ticketId = it.ticketId,
            oldStatus = it.oldStatus?.let { s -> TicketStatus.valueOf(s) },
            newStatus = TicketStatus.valueOf(it.newStatus),
            changedAtMillis = DbTime.parse(it.changedAt),
        )
    },
)
