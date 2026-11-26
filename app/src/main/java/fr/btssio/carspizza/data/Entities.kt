package fr.btssio.carspizza.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * Entités Room : une par table du schéma `docs/schema.sql` (mêmes tables, mêmes colonnes, mêmes
 * clés étrangères). Prix en centimes. Les dates sont des TEXT « yyyy-MM-dd HH:mm:ss » (UTC).
 *
 * Room ne sait pas générer les contraintes CHECK du schéma SQL (quantité > 0, statuts autorisés...) :
 * elles sont appliquées par le code (`OrderRepository`, formulaires) et par les enums.
 */

// ---- 1. pizza ------------------------------------------------------------------------------------

@Entity(tableName = "pizza", indices = [Index(value = ["name"], unique = true)])
data class PizzaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    /** Prix en centimes. */
    val price: Int,
    /** Temps de préparation estimé, en minutes. */
    @ColumnInfo(name = "preparation_time", defaultValue = "15") val preparationTime: Int = 15,
    @ColumnInfo(name = "is_available", defaultValue = "1") val isAvailable: Boolean = true,
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String? = null,
)

// ---- 2. ticket -----------------------------------------------------------------------------------

@Entity(tableName = "ticket", indices = [
    Index(value = ["ticket_number"], unique = true),
    Index(value = ["status"], name = "idx_ticket_status"),
    Index(value = ["created_at"], name = "idx_ticket_created_at"),
])
data class TicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ticket_number") val ticketNumber: String,
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP") val createdAt: String,
    /** PENDING, PREPARING, COMPLETED ou CANCELLED (voir [TicketStatus]). */
    @ColumnInfo(defaultValue = "'PENDING'") val status: String = TicketStatus.PENDING.name,
    @ColumnInfo(name = "total_price", defaultValue = "0") val totalPrice: Int = 0,
)

// ---- 3. ticket_item ------------------------------------------------------------------------------

@Entity(
    tableName = "ticket_item",
    foreignKeys = [
        ForeignKey(
            entity = TicketEntity::class,
            parentColumns = ["id"],
            childColumns = ["ticket_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        // La pizza peut être supprimée du menu : la ligne garde son nom et son prix.
        ForeignKey(
            entity = PizzaEntity::class,
            parentColumns = ["id"],
            childColumns = ["pizza_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["ticket_id"], name = "idx_ticket_item_ticket"),
        Index(value = ["pizza_id"]),
    ],
)
data class TicketItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ticket_id") val ticketId: Long,
    @ColumnInfo(name = "pizza_id") val pizzaId: Long?,
    /** Nom et prix copiés au moment de la commande. */
    @ColumnInfo(name = "pizza_name") val pizzaName: String,
    val quantity: Int,
    @ColumnInfo(name = "unit_price") val unitPrice: Int,
    val subtotal: Int,
)

// ---- 4. ingredient (stock : hors périmètre v1, table prête) ---------------------------------------

@Entity(tableName = "ingredient", indices = [Index(value = ["name"], unique = true)])
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** « g », « ml » ou « pcs ». */
    val unit: String,
    @ColumnInfo(name = "current_quantity", defaultValue = "0") val currentQuantity: Int = 0,
    @ColumnInfo(name = "minimum_quantity", defaultValue = "0") val minimumQuantity: Int = 0,
    /** Prix d'achat en centimes. */
    @ColumnInfo(name = "purchase_price", defaultValue = "0") val purchasePrice: Int = 0,
    @ColumnInfo(name = "is_available", defaultValue = "1") val isAvailable: Boolean = true,
)

// ---- 5. pizza_ingredient -------------------------------------------------------------------------

@Entity(
    tableName = "pizza_ingredient",
    foreignKeys = [
        ForeignKey(
            entity = PizzaEntity::class,
            parentColumns = ["id"],
            childColumns = ["pizza_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["pizza_id", "ingredient_id"], unique = true),
        Index(value = ["ingredient_id"]),
    ],
)
data class PizzaIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "pizza_id") val pizzaId: Long,
    @ColumnInfo(name = "ingredient_id") val ingredientId: Long,
    /** Quantité nécessaire pour une pizza. */
    @ColumnInfo(name = "quantity_required") val quantityRequired: Int,
)

// ---- 6. stock_movement ---------------------------------------------------------------------------

@Entity(
    tableName = "stock_movement",
    foreignKeys = [
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredient_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["ingredient_id"], name = "idx_stock_movement_ingredient")],
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ingredient_id") val ingredientId: Long,
    /** « IN », « OUT » ou « ADJUSTMENT ». */
    @ColumnInfo(name = "movement_type") val movementType: String,
    val quantity: Int,
    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP") val createdAt: String,
    val reason: String? = null,
)

// ---- 7. ticket_status_history --------------------------------------------------------------------
// Table indépendante, SANS clé étrangère : `ticket_id` est une simple valeur, l'historique survit au ticket.

@Entity(
    tableName = "ticket_status_history",
    indices = [
        Index(value = ["ticket_id"], name = "idx_history_ticket"),
        Index(value = ["changed_at"], name = "idx_history_changed_at"),
    ],
)
data class TicketStatusHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ticket_id") val ticketId: Long,
    @ColumnInfo(name = "old_status") val oldStatus: String? = null,
    @ColumnInfo(name = "new_status") val newStatus: String,
    @ColumnInfo(name = "changed_at", defaultValue = "CURRENT_TIMESTAMP") val changedAt: String,
)

// ---- Relation : un ticket avec ses lignes et son historique ---------------------------------------

data class TicketWithRelations(
    @Embedded val ticket: TicketEntity,
    @Relation(parentColumn = "id", entityColumn = "ticket_id") val items: List<TicketItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "ticket_id") val history: List<TicketStatusHistoryEntity>,
)
