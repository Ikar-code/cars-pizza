package fr.btssio.voxresto.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PizzaDao {
    @Query("SELECT * FROM pizza ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PizzaEntity>>

    @Query("SELECT * FROM pizza WHERE is_available = 1 ORDER BY name COLLATE NOCASE")
    suspend fun available(): List<PizzaEntity>

    @Query("SELECT * FROM pizza WHERE id = :id")
    suspend fun find(id: Long): PizzaEntity?

    /** Lève `SQLiteConstraintException` si le nom existe déjà (index unique). */
    @Insert
    suspend fun insert(pizza: PizzaEntity): Long

    @Query(
        "UPDATE pizza SET name = :name, description = :description, price = :price, " +
            "preparation_time = :preparationTime, updated_at = :updatedAt WHERE id = :id"
    )
    suspend fun update(id: Long, name: String, description: String?, price: Int, preparationTime: Int, updatedAt: String)

    @Query("UPDATE pizza SET is_available = :available, updated_at = :updatedAt WHERE id = :id")
    suspend fun setAvailable(id: Long, available: Boolean, updatedAt: String)

    /** `ticket_item.pizza_id` passe à NULL (ON DELETE SET NULL) : les anciens tickets restent lisibles. */
    @Query("DELETE FROM pizza WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TicketDao {
    @Insert
    suspend fun insertTicket(ticket: TicketEntity): Long

    @Insert
    suspend fun insertItems(items: List<TicketItemEntity>)

    @Insert
    suspend fun insertHistory(entry: TicketStatusHistoryEntity)

    /** Plus grand rang déjà utilisé ce jour-là (0 s'il n'y en a pas). [prefix] : « 20261125-% ». */
    @Query(
        "SELECT COALESCE(MAX(CAST(SUBSTR(ticket_number, 10) AS INTEGER)), 0) " +
            "FROM ticket WHERE ticket_number LIKE :prefix"
    )
    suspend fun lastSequence(prefix: String): Long

    @Query("SELECT status FROM ticket WHERE id = :id")
    suspend fun statusOf(id: Long): String?

    @Query("UPDATE ticket SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    /** Tickets en cours + ceux des 7 derniers jours (pour l'historique), avec lignes et changements d'état. */
    @Transaction
    @Query(
        "SELECT * FROM ticket WHERE status IN ('PENDING', 'PREPARING') " +
            "OR created_at >= datetime('now', '-7 days') ORDER BY created_at, id"
    )
    fun observeRecent(): Flow<List<TicketWithRelations>>
}
