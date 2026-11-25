package fr.btssio.voxresto.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuDao {
    @Query("SELECT * FROM menu_items ORDER BY category, name")
    fun observeAll(): Flow<List<MenuItem>>

    @Query("SELECT * FROM menu_items WHERE available = 1")
    suspend fun getAvailable(): List<MenuItem>

    @Query("SELECT * FROM menu_items WHERE id = :id")
    suspend fun getById(id: Long): MenuItem?

    @Query("SELECT COUNT(*) FROM menu_items")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: MenuItem): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<MenuItem>)

    @Query("UPDATE menu_items SET available = :available WHERE id = :id")
    suspend fun setAvailable(id: Long, available: Boolean)
}

@Dao
interface TicketDao {
    @Insert
    suspend fun insertTicket(ticket: Ticket): Long

    @Update
    suspend fun updateTicket(ticket: Ticket)

    @Query("SELECT * FROM tickets WHERE id = :id")
    suspend fun getById(id: Long): Ticket?

    @Query("SELECT * FROM tickets WHERE status = 'DRAFT' ORDER BY id DESC LIMIT 1")
    suspend fun findDraft(): Ticket?

    @Transaction
    @Query("SELECT * FROM tickets WHERE status = 'DRAFT' ORDER BY id DESC LIMIT 1")
    fun observeDraft(): Flow<TicketWithDetails?>

    @Transaction
    @Query("SELECT * FROM tickets WHERE status = 'DRAFT' ORDER BY id DESC LIMIT 1")
    suspend fun getDraftWithDetails(): TicketWithDetails?

    @Transaction
    @Query("SELECT * FROM tickets WHERE id = :id")
    suspend fun getWithDetails(id: Long): TicketWithDetails?

    @Transaction
    @Query("SELECT * FROM tickets WHERE status != 'DRAFT' ORDER BY updated_at DESC")
    fun observeHistory(): Flow<List<TicketWithDetails>>

    @Query("SELECT MAX(number) FROM tickets WHERE confirmed_at >= :startOfDay")
    suspend fun maxNumberSince(startOfDay: Long): Int?

    @Query("SELECT * FROM ticket_lines WHERE ticket_id = :ticketId AND menu_item_id = :menuItemId LIMIT 1")
    suspend fun findLine(ticketId: Long, menuItemId: Long): TicketLine?

    @Insert
    suspend fun insertLine(line: TicketLine): Long

    @Update
    suspend fun updateLine(line: TicketLine)

    @Delete
    suspend fun deleteLine(line: TicketLine)

    @Insert
    suspend fun insertEvent(event: TicketEvent)
}
