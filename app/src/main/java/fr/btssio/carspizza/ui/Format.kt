package fr.btssio.carspizza.ui

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    /** 1250 → « 12,50 € » */
    fun price(cents: Int): String =
        NumberFormat.getCurrencyInstance(Locale.FRANCE).format(cents / 100.0)

    fun dateTime(millis: Long): String =
        SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE).format(Date(millis))

    fun time(millis: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date(millis))
}
