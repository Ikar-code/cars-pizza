package fr.btssio.voxresto.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Plat ou boisson de la carte. [aliases] = synonymes utilisables à la voix, séparés par des virgules. */
@Entity(tableName = "menu_items", indices = [Index(value = ["name"], unique = true)])
data class MenuItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    @ColumnInfo(name = "price_cents") val priceCents: Int,
    val aliases: String = "",
    val available: Boolean = true,
) {
    val aliasList: List<String>
        get() = aliases.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

enum class TicketStatus { DRAFT, CONFIRMED, READY, CANCELLED }

/** Ticket de commande. Un seul ticket DRAFT existe à la fois : c'est la commande en cours de dictée. */
@Entity(tableName = "tickets")
data class Ticket(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Numéro lisible attribué à la validation, remis à 1 chaque jour. */
    val number: Int? = null,
    val status: TicketStatus = TicketStatus.DRAFT,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "confirmed_at") val confirmedAt: Long? = null,
)

/**
 * Ligne de ticket. Le nom et le prix sont copiés au moment de la commande :
 * modifier la carte plus tard ne réécrit pas l'historique.
 */
@Entity(
    tableName = "ticket_lines",
    foreignKeys = [
        ForeignKey(
            entity = Ticket::class,
            parentColumns = ["id"],
            childColumns = ["ticket_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MenuItem::class,
            parentColumns = ["id"],
            childColumns = ["menu_item_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("ticket_id"), Index("menu_item_id")],
)
data class TicketLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ticket_id") val ticketId: Long,
    @ColumnInfo(name = "menu_item_id") val menuItemId: Long?,
    val name: String,
    @ColumnInfo(name = "unit_price_cents") val unitPriceCents: Int,
    val quantity: Int,
)

enum class EventType { CREATED, ITEM_ADDED, ITEM_REMOVED, CONFIRMED, READY, CANCELLED }

/** Origine d'une action : permet de distinguer ce qui vient de la voix de ce qui vient du tactile. */
enum class InputSource { VOICE, TEXT, TOUCH }

/** Journal d'audit d'un ticket : c'est la base de l'écran « Historique ». */
@Entity(
    tableName = "ticket_events",
    foreignKeys = [
        ForeignKey(
            entity = Ticket::class,
            parentColumns = ["id"],
            childColumns = ["ticket_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("ticket_id")],
)
data class TicketEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ticket_id") val ticketId: Long,
    val timestamp: Long,
    val type: EventType,
    val detail: String,
    val source: InputSource,
)

/** Ticket + lignes + événements, chargés en une seule transaction. */
data class TicketWithDetails(
    @Embedded val ticket: Ticket,
    @Relation(parentColumn = "id", entityColumn = "ticket_id")
    val lines: List<TicketLine>,
    @Relation(parentColumn = "id", entityColumn = "ticket_id")
    val events: List<TicketEvent>,
) {
    val totalCents: Int
        get() = lines.sumOf { it.unitPriceCents * it.quantity }
    val itemCount: Int
        get() = lines.sumOf { it.quantity }
}
