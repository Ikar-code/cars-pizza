package fr.btssio.voxresto.data

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.SQLException
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ConfirmResult {
    data object Empty : ConfirmResult
    data class Confirmed(val shortNumber: Int, val totalCents: Int) : ConfirmResult
    data class Failed(val reason: String) : ConfirmResult
}

/**
 * Point d'entrée unique vers les données.
 *
 * - La commande en cours de dictée ([draft]) vit en mémoire.
 * - [confirmDraft] l'enregistre en une seule transaction : ticket + lignes + première entrée
 *   d'historique, ou rien du tout. Une commande validée n'est donc jamais perdue ni à moitié écrite.
 */
class OrderRepository(
    private val db: PizzeriaDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val draftLock = Mutex()
    private val _draft = MutableStateFlow(DraftOrder())

    val draft: StateFlow<DraftOrder> = _draft.asStateFlow()
    val pizzas: Flow<List<Pizza>> = db.observe { queryPizzas(it, onlyAvailable = false) }

    /** Tickets en cours + ceux des 7 derniers jours, par ordre d'arrivée. */
    val tickets: Flow<List<TicketWithDetails>> = db.observe { queryTickets(it) }

    suspend fun availablePizzas(): List<Pizza> = db.read { queryPizzas(it, onlyAvailable = true) }

    fun currentDraft(): DraftOrder = _draft.value

    // ---- Menu (F9) ---------------------------------------------------------------------------

    /** @return false si une pizza porte déjà ce nom. */
    suspend fun createPizza(input: PizzaInput): Boolean = try {
        db.write { it.insertOrThrow("pizza", null, input.toValues()) }
        true
    } catch (e: SQLiteConstraintException) {
        false
    }

    /** @return false si une autre pizza porte déjà ce nom. */
    suspend fun updatePizza(id: Long, input: PizzaInput): Boolean = try {
        db.write { d ->
            val values = input.toValues().apply { put("updated_at", DbTime.format(clock())) }
            d.update("pizza", values, "id = ?", arrayOf(id.toString()))
        }
        true
    } catch (e: SQLiteConstraintException) {
        false
    }

    suspend fun setAvailable(id: Long, available: Boolean) {
        db.write { d ->
            val values = ContentValues().apply {
                put("is_available", if (available) 1 else 0)
                put("updated_at", DbTime.format(clock()))
            }
            d.update("pizza", values, "id = ?", arrayOf(id.toString()))
        }
    }

    /** Les anciens tickets gardent le nom et le prix copiés ; la pizza est aussi retirée du brouillon. */
    suspend fun deletePizza(id: Long) {
        draftLock.withLock {
            db.write { it.delete("pizza", "id = ?", arrayOf(id.toString())) }
            _draft.update { draft -> draft.copy(lines = draft.lines.filterNot { it.pizzaId == id }) }
        }
    }

    // ---- Commande en cours (F3, F5, F8) ------------------------------------------------------

    /** @return false si la pizza n'existe pas ou n'est plus disponible. */
    suspend fun addToDraft(pizzaId: Long, quantity: Int): Boolean {
        return draftLock.withLock {
            val pizza = db.read { queryPizza(it, pizzaId) }
            if (pizza == null || !pizza.isAvailable) return@withLock false
            val qty = quantity.coerceIn(1, MAX_QUANTITY)
            _draft.update { draft ->
                val exists = draft.lines.any { it.pizzaId == pizzaId }
                val lines = if (exists) {
                    draft.lines.map {
                        if (it.pizzaId == pizzaId) it.copy(quantity = (it.quantity + qty).coerceAtMost(MAX_QUANTITY)) else it
                    }
                } else {
                    draft.lines + DraftLine(pizza.id, pizza.name, pizza.priceCents, qty)
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
                val ticket = db.write { insertTicket(it, lines, clock()) }
                _draft.value = DraftOrder()
                ConfirmResult.Confirmed(ticket.shortNumber, ticket.totalCents)
            } catch (e: SQLException) {
                // La commande reste en cours : l'employé peut réessayer, rien n'est perdu.
                ConfirmResult.Failed(e.message ?: "erreur de base de données")
            }
        }
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

    private suspend fun changeStatus(ticketId: Long, next: (TicketStatus) -> TicketStatus?): Boolean =
        db.write { d ->
            val args = arrayOf(ticketId.toString())
            val current = d.rawQuery("SELECT status FROM ticket WHERE id = ?", args).use { c ->
                if (c.moveToFirst()) TicketStatus.valueOf(c.getString(0)) else null
            } ?: return@write false
            val target = next(current) ?: return@write false
            d.update("ticket", ContentValues().apply { put("status", target.name) }, "id = ?", args)
            d.insertOrThrow(
                "ticket_status_history",
                null,
                ContentValues().apply {
                    put("ticket_id", ticketId)
                    put("old_status", current.name)
                    put("new_status", target.name)
                },
            )
            true
        }

    companion object {
        const val MAX_QUANTITY = 99
    }
}

// ---- Écriture --------------------------------------------------------------------------------

private fun PizzaInput.toValues() = ContentValues().apply {
    put("name", name.trim())
    put("description", description?.trim()?.takeIf { it.isNotEmpty() })
    put("price", priceCents)
    put("preparation_time", preparationMinutes)
}

private fun insertTicket(db: SQLiteDatabase, lines: List<DraftLine>, now: Long): Ticket {
    // Numéro « yyyyMMdd-NNN » : NNN repart à 001 chaque jour. MAX + 1 reste unique même si la
    // journée contient des tickets annulés.
    val day = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date(now))
    val last = DatabaseUtils.longForQuery(
        db,
        "SELECT COALESCE(MAX(CAST(SUBSTR(ticket_number, 10) AS INTEGER)), 0) FROM ticket WHERE ticket_number LIKE ?",
        arrayOf("$day-%"),
    )
    val number = "%s-%03d".format(Locale.ROOT, day, last + 1)
    val total = lines.sumOf { it.unitPriceCents * it.quantity }

    val ticketId = db.insertOrThrow(
        "ticket",
        null,
        ContentValues().apply {
            put("ticket_number", number)
            put("total_price", total)
        },
    )
    lines.forEach { line ->
        db.insertOrThrow(
            "ticket_item",
            null,
            ContentValues().apply {
                put("ticket_id", ticketId)
                put("pizza_id", line.pizzaId)
                put("pizza_name", line.name)
                put("quantity", line.quantity)
                put("unit_price", line.unitPriceCents)
                put("subtotal", line.unitPriceCents * line.quantity)
            },
        )
    }
    db.insertOrThrow(
        "ticket_status_history",
        null,
        ContentValues().apply {
            put("ticket_id", ticketId)
            put("new_status", TicketStatus.PENDING.name)
        },
    )
    return Ticket(ticketId, number, now, TicketStatus.PENDING, total)
}

// ---- Lecture ---------------------------------------------------------------------------------

private const val PIZZA_COLUMNS = "id, name, description, price, preparation_time, is_available"

private fun readPizza(c: Cursor) = Pizza(
    id = c.getLong(0),
    name = c.getString(1),
    description = if (c.isNull(2)) null else c.getString(2),
    priceCents = c.getInt(3),
    preparationMinutes = c.getInt(4),
    isAvailable = c.getInt(5) == 1,
)

private fun queryPizzas(db: SQLiteDatabase, onlyAvailable: Boolean): List<Pizza> {
    val where = if (onlyAvailable) "WHERE is_available = 1" else ""
    return db.rawQuery("SELECT $PIZZA_COLUMNS FROM pizza $where ORDER BY name COLLATE NOCASE", null).use { c ->
        buildList { while (c.moveToNext()) add(readPizza(c)) }
    }
}

private fun queryPizza(db: SQLiteDatabase, id: Long): Pizza? =
    db.rawQuery("SELECT $PIZZA_COLUMNS FROM pizza WHERE id = ?", arrayOf(id.toString())).use { c ->
        if (c.moveToFirst()) readPizza(c) else null
    }

private fun queryTickets(db: SQLiteDatabase): List<TicketWithDetails> {
    val tickets = db.rawQuery(
        """
        SELECT id, ticket_number, created_at, status, total_price
        FROM ticket
        WHERE status IN ('PENDING', 'PREPARING') OR created_at >= datetime('now', '-7 days')
        ORDER BY created_at, id
        """.trimIndent(),
        null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    Ticket(
                        id = c.getLong(0),
                        number = c.getString(1),
                        createdAtMillis = DbTime.parse(c.getString(2)),
                        status = TicketStatus.valueOf(c.getString(3)),
                        totalCents = c.getInt(4),
                    )
                )
            }
        }
    }
    if (tickets.isEmpty()) return emptyList()

    // Les identifiants viennent de la base (entiers) : pas de risque d'injection.
    val ids = tickets.joinToString(",") { it.id.toString() }

    val items = db.rawQuery(
        "SELECT id, ticket_id, pizza_id, pizza_name, quantity, unit_price, subtotal " +
            "FROM ticket_item WHERE ticket_id IN ($ids) ORDER BY id",
        null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    TicketItem(
                        id = c.getLong(0),
                        ticketId = c.getLong(1),
                        pizzaId = if (c.isNull(2)) null else c.getLong(2),
                        pizzaName = c.getString(3),
                        quantity = c.getInt(4),
                        unitPriceCents = c.getInt(5),
                        subtotalCents = c.getInt(6),
                    )
                )
            }
        }
    }.groupBy { it.ticketId }

    val history = db.rawQuery(
        "SELECT id, ticket_id, old_status, new_status, changed_at " +
            "FROM ticket_status_history WHERE ticket_id IN ($ids) ORDER BY changed_at, id",
        null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    StatusChange(
                        id = c.getLong(0),
                        ticketId = c.getLong(1),
                        oldStatus = if (c.isNull(2)) null else TicketStatus.valueOf(c.getString(2)),
                        newStatus = TicketStatus.valueOf(c.getString(3)),
                        changedAtMillis = DbTime.parse(c.getString(4)),
                    )
                )
            }
        }
    }.groupBy { it.ticketId }

    return tickets.map { TicketWithDetails(it, items[it.id].orEmpty(), history[it.id].orEmpty()) }
}
