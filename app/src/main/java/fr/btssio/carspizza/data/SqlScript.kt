package fr.btssio.carspizza.data

/** Découpe un script SQL (commentaires « -- » inclus) en instructions exécutables une par une. */
object SqlScript {
    /**
     * Les `PRAGMA` sont ignorés : `PRAGMA foreign_keys = ON` n'a aucun effet dans la transaction
     * de création, les clés étrangères sont activées dans `PizzeriaDatabase.onConfigure`.
     */
    fun statements(script: String): List<String> =
        script.lineSequence()
            .map { it.substringBefore("--") }
            .joinToString("\n")
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("PRAGMA", ignoreCase = true) }
}
