package fr.btssio.voxresto.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Base SQLite locale (cahier des charges §8 : stockage sur l'appareil, aucun serveur).
 *
 * Le schéma est celui du fichier `assets/db/schema.sql`, exécuté tel quel (contraintes CHECK,
 * clés étrangères, index), puis le menu de départ de `assets/db/seed.sql`.
 */
class PizzeriaDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    private val assets = context.applicationContext.assets

    /** Incrémenté après chaque écriture : les `observe` relisent alors la base. */
    private val changes = MutableStateFlow(0)

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Équivalent fiable de « PRAGMA foreign_keys = ON » (ce PRAGMA est sans effet dans une transaction).
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        runScript(db, "db/schema.sql")
        runScript(db, "db/seed.sql")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 : rien à migrer. Les prochaines versions du schéma s'ajouteront ici.
    }

    private fun runScript(db: SQLiteDatabase, assetPath: String) {
        val script = assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        SqlScript.statements(script).forEach { db.execSQL(it) }
    }

    // ---- Accès coroutines --------------------------------------------------------------------

    /** Relit la base au premier abonnement, puis après chaque écriture. */
    fun <T> observe(query: (SQLiteDatabase) -> T): Flow<T> =
        changes.map { withContext(Dispatchers.IO) { query(readableDatabase) } }

    suspend fun <T> read(query: (SQLiteDatabase) -> T): T =
        withContext(Dispatchers.IO) { query(readableDatabase) }

    /**
     * Exécute [block] dans une transaction : tout est enregistré, ou rien du tout si une
     * exception survient (elle est alors relancée à l'appelant).
     */
    suspend fun <T> write(block: (SQLiteDatabase) -> T): T {
        val result = withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val value = block(db)
                db.setTransactionSuccessful()
                value
            } finally {
                db.endTransaction()
            }
        }
        changes.update { it + 1 }
        return result
    }

    companion object {
        const val DB_NAME = "pizzeria.db"
        const val DB_VERSION = 1
    }
}
