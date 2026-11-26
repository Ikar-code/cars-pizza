package fr.btssio.carspizza.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Dates de la base : `CURRENT_TIMESTAMP` de SQLite fournit « yyyy-MM-dd HH:mm:ss » en UTC.
 * On les convertit en millisecondes pour l'affichage (fuseau local appliqué par `Format`).
 */
object DbTime {
    private const val PATTERN = "yyyy-MM-dd HH:mm:ss"

    fun parse(text: String?): Long =
        text?.let { runCatching { formatter().parse(it)?.time }.getOrNull() } ?: 0L

    fun format(millis: Long): String = formatter().format(Date(millis))

    // SimpleDateFormat n'est pas thread-safe : une instance par appel.
    private fun formatter() = SimpleDateFormat(PATTERN, Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}
