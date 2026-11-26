package fr.btssio.carspizza.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base SQLite locale (cahier des charges §8 : stockage sur l'appareil, aucun serveur).
 * Au premier lancement, le menu de départ de `assets/db/seed.sql` est inséré.
 *
 * Room génère le schéma dans `app/schemas/` au build (utile pour le dossier : MLD).
 */
@Database(
    entities = [
        PizzaEntity::class,
        TicketEntity::class,
        TicketItemEntity::class,
        IngredientEntity::class,
        PizzaIngredientEntity::class,
        StockMovementEntity::class,
        TicketStatusHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PizzeriaDatabase : RoomDatabase() {
    abstract fun pizzaDao(): PizzaDao
    abstract fun ticketDao(): TicketDao

    companion object {
        fun create(context: Context): PizzeriaDatabase {
            val app = context.applicationContext
            return Room.databaseBuilder(app, PizzeriaDatabase::class.java, "pizzeria_room.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val script = app.assets.open("db/seed.sql").bufferedReader(Charsets.UTF_8).use { it.readText() }
                        SqlScript.statements(script).forEach { db.execSQL(it) }
                    }
                })
                .build()
        }
    }
}
