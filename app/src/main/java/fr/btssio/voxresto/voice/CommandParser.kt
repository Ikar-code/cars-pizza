package fr.btssio.voxresto.voice

import java.text.Normalizer
import java.util.Locale

/** Vue minimale d'un plat pour le parseur (aucune dépendance Android : testable en JVM pur). */
data class MenuEntry(val id: Long, val name: String, val aliases: List<String> = emptyList())

/** Résultat du rapprochement d'un nom de plat isolé avec le menu (voir [CommandParser.resolve]). */
sealed interface MenuMatch {
    data class Found(val entry: MenuEntry) : MenuMatch
    data class Ambiguous(val names: List<String>) : MenuMatch
    data object NotFound : MenuMatch
}

sealed interface VoiceCommand {
    data class Add(val itemId: Long, val itemName: String, val quantity: Int) : VoiceCommand
    data class Remove(val itemId: Long, val itemName: String, val quantity: Int?) : VoiceCommand
    data class Ambiguous(val heard: String, val options: List<String>) : VoiceCommand
    data class Unknown(val heard: String) : VoiceCommand
    data object Confirm : VoiceCommand
    data object CancelOrder : VoiceCommand
    data object ReadBack : VoiceCommand
}

/**
 * Transforme une phrase reconnue (« deux margherita et une reine, c'est tout ») en commandes.
 *
 * Étapes : normalisation (minuscules, sans accents) → détection des ordres globaux
 * (valider, annuler, récapitulatif) → découpage en segments (« et », « puis ») →
 * quantité + verbe d'action → rapprochement flou avec la carte (distance de Levenshtein).
 */
class CommandParser(menu: List<MenuEntry>) {

    private class Candidate(val entry: MenuEntry, val tokens: List<String>)

    private val candidates: List<Candidate> = menu.flatMap { entry ->
        (listOf(entry.name) + entry.aliases).mapNotNull { label ->
            val tokens = contentTokens(label)
            if (tokens.isEmpty()) null else Candidate(entry, tokens)
        }
    }

    fun parse(raw: String): List<VoiceCommand> {
        var text = normalize(raw)
        if (text.isEmpty()) return emptyList()

        if (CANCEL_ORDER.containsMatchIn(text)) return listOf(VoiceCommand.CancelOrder)

        val wantsConfirm = CONFIRM.containsMatchIn(text)
        text = CONFIRM.replace(text, " ")
        val wantsReadBack = READ_BACK.containsMatchIn(text)
        text = READ_BACK.replace(text, " ")

        val commands = mutableListOf<VoiceCommand>()
        var removing = false

        for (segment in splitSegments(text)) {
            var tokens = segment
            // Verbes d'action : ils fixent le mode (ajout / retrait) pour ce segment et les suivants.
            val verbs = tokens.filter { it in ADD_VERBS || it in REMOVE_VERBS }
            if (verbs.isNotEmpty()) removing = verbs.last() in REMOVE_VERBS
            tokens = tokens.filterNot { it in ADD_VERBS || it in REMOVE_VERBS || it in FILLERS }

            var quantity: Int? = null
            if (tokens.isNotEmpty()) {
                quantity = parseQuantity(tokens.first())
                if (quantity != null) tokens = tokens.drop(1)
            }
            tokens = tokens.filterNot { it in ARTICLES }.map(::stem)
            if (tokens.isEmpty()) continue

            val heard = tokens.joinToString(" ")
            when (val found = findItem(tokens)) {
                Match.None -> commands += VoiceCommand.Unknown(heard)
                is Match.Several -> commands += VoiceCommand.Ambiguous(heard, found.names)
                is Match.One -> commands += if (removing) {
                    VoiceCommand.Remove(found.entry.id, found.entry.name, quantity)
                } else {
                    VoiceCommand.Add(found.entry.id, found.entry.name, (quantity ?: 1).coerceIn(1, 99))
                }
            }
        }

        if (wantsReadBack) commands += VoiceCommand.ReadBack
        if (wantsConfirm) commands += VoiceCommand.Confirm
        return commands
    }

    /**
     * Retrouve un plat à partir d'un nom isolé (« margarita », « la reine »...). Sert aux appels
     * d'outils de Gemini : le modèle comprend la phrase, mais c'est le menu en base qui tranche.
     */
    fun resolve(raw: String): MenuMatch {
        val tokens = contentTokens(raw)
        if (tokens.isEmpty()) return MenuMatch.NotFound
        return when (val found = findItem(tokens)) {
            Match.None -> MenuMatch.NotFound
            is Match.One -> MenuMatch.Found(found.entry)
            is Match.Several -> MenuMatch.Ambiguous(found.names)
        }
    }

    /**
     * Les moteurs vocaux renvoient plusieurs hypothèses. On garde la première qui est
     * la mieux comprise (le plus de commandes valides, le moins d'inconnues).
     */
    fun parseBest(alternatives: List<String>): List<VoiceCommand> {
        var best: List<VoiceCommand> = emptyList()
        var bestQuality = Int.MIN_VALUE
        for (alternative in alternatives) {
            val commands = parse(alternative)
            val q = quality(commands)
            // Strictement supérieur : à qualité égale, on garde l'hypothèse la plus probable (la première).
            if (q > bestQuality) {
                best = commands
                bestQuality = q
            }
        }
        return best
    }

    private fun quality(commands: List<VoiceCommand>): Int =
        commands.sumOf { command ->
            // Type explicite : sans lui, `sumOf` est ambigu entre Int et Long avec des littéraux.
            val points: Int = when (command) {
                is VoiceCommand.Unknown -> -3
                is VoiceCommand.Ambiguous -> -1
                else -> 2
            }
            points
        }

    // ---- Rapprochement avec la carte ---------------------------------------------------------

    private sealed interface Match {
        data object None : Match
        data class One(val entry: MenuEntry) : Match
        data class Several(val names: List<String>) : Match
    }

    private fun findItem(segment: List<String>): Match {
        // « une pizza » toute seule, alors qu'aucune pizza du menu ne contient ce mot dans son nom
        // (Margherita, Reine...) : on demande de préciser parmi tout le menu.
        if (candidates.isNotEmpty() && segment.all { it == "pizza" } && candidates.none { "pizza" in it.tokens }) {
            return Match.Several(candidates.map { it.entry.name }.distinct().sorted())
        }

        // Meilleur score par plat (un plat a plusieurs libellés : nom + alias).
        val best = HashMap<Long, Pair<Double, Candidate>>()
        for (c in candidates) {
            val score = score(segment, c.tokens)
            if (score <= 0.0) continue
            val current = best[c.entry.id]
            if (current == null || score > current.first) best[c.entry.id] = score to c
        }
        if (best.isEmpty()) return Match.None

        val ranked = best.values.sortedByDescending { it.first }
        val top = ranked.first().first
        val isPartial = top <= PARTIAL_WEIGHT
        val tied = ranked.filter { top - it.first < 0.001 }
        // « une pizza » correspond à plusieurs plats avec la même force : on demande de préciser.
        if (isPartial && tied.size > 1) return Match.Several(tied.map { it.second.entry.name }.sorted())
        return Match.One(ranked.first().second.entry)
    }

    /**
     * Score d'un libellé du menu par rapport à ce qui a été entendu.
     * - correspondance complète (tous les mots du libellé sont dans la phrase) : ~1.0
     * - correspondance partielle (« margherita » pour « pizza margherita ») : 0.8 max
     */
    private fun score(heard: List<String>, label: List<String>): Double {
        val labelHits = label.map { l -> heard.maxOf { h -> similarity(h, l) } }
        if (labelHits.all { it > 0.0 }) return labelHits.average() + 0.01 * label.size

        val heardHits = heard.map { h -> label.maxOf { l -> similarity(h, l) } }
        if (heardHits.all { it > 0.0 }) return PARTIAL_WEIGHT * heardHits.average()
        return 0.0
    }

    /** 0.0 si les mots sont trop différents, sinon une similarité entre 0 et 1. */
    private fun similarity(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        val tolerance = when {
            maxLen <= 4 -> 0
            maxLen <= 7 -> 1
            else -> 2
        }
        val distance = levenshtein(a, b)
        return if (distance <= tolerance) 1.0 - distance.toDouble() / maxLen else 0.0
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    // ---- Texte -------------------------------------------------------------------------------

    private fun splitSegments(text: String): List<List<String>> {
        val segments = mutableListOf<MutableList<String>>(mutableListOf())
        for (token in text.split(' ').filter { it.isNotEmpty() }) {
            if (token in SEPARATORS) segments += mutableListOf<String>() else segments.last() += token
        }
        return segments.filter { it.isNotEmpty() }
    }

    private fun contentTokens(label: String): List<String> =
        normalize(label).split(' ').filter { it.isNotEmpty() && it !in ARTICLES }.map(::stem)

    private fun parseQuantity(token: String): Int? = token.toIntOrNull() ?: NUMBER_WORDS[token]

    companion object {
        private const val PARTIAL_WEIGHT = 0.8

        private val CANCEL_ORDER = Regex(
            """\b((annule|annuler|supprime|supprimer|efface|effacer|vide|vider)\s+(toute\s+|tout\s+)?(la|ma|cette)\s+commande|tout\s+annuler|recommence|repartons?\s+de\s+zero)\b"""
        )
        private val CONFIRM = Regex(
            """\b((valide|valider|confirme|confirmer|envoie|envoyer|termine|terminer)(\s+(la|ma|cette))?(\s+commande)?|c\s+est\s+tout|ce\s+sera\s+tout|c\s+est\s+bon)\b"""
        )
        private val READ_BACK = Regex(
            """\b(recapitulatif|recap|lis\s+(moi\s+)?(la\s+)?commande|lis\s+moi|qu\s+est\s+ce\s+que\s+j\s+ai\s+(pris|commande)|c\s+est\s+combien|combien|total|montant|l\s+addition)\b"""
        )

        private val ADD_VERBS = setOf(
            "ajoute", "ajouter", "rajoute", "rajouter", "mets", "mettre", "donne", "prends", "prendre",
            "voudrais", "veux", "aimerais", "commande", "commander", "prepare", "preparer",
        )
        private val REMOVE_VERBS = setOf(
            "enleve", "enlever", "retire", "retirer", "supprime", "supprimer", "annule", "annuler", "sans",
        )
        private val FILLERS = setOf(
            "je", "j", "s", "il", "vous", "plait", "te", "moi", "me", "svp", "aussi", "plus", "encore",
            "peut", "on", "pourrais", "pourriez", "pouvez", "bien", "alors", "bonjour", "salut", "merci",
            "ok", "voila", "donc", "euh", "ben", "vais", "ca", "ce", "cette", "ma", "mon",
            "fais", "fait", "dis", "affiche", "lis",
        )
        private val ARTICLES = setOf("le", "la", "les", "l", "de", "du", "des", "d", "au", "aux")
        private val SEPARATORS = setOf("et", "puis", "ensuite", "apres")

        private val NUMBER_WORDS = mapOf(
            "un" to 1, "une" to 1, "deux" to 2, "trois" to 3, "quatre" to 4, "cinq" to 5, "six" to 6,
            "sept" to 7, "huit" to 8, "neuf" to 9, "dix" to 10, "onze" to 11, "douze" to 12,
            "treize" to 13, "quatorze" to 14, "quinze" to 15, "seize" to 16, "vingt" to 20,
        )

        /** Minuscules, sans accents ni ponctuation : « Jus d'Orange ! » → « jus d orange ». */
        fun normalize(input: String): String =
            Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()

        /** Pluriel simplifié : « reines » → « reine », « gateaux » → « gateau ». */
        fun stem(token: String): String =
            if (token.length > 3 && (token.endsWith("s") || token.endsWith("x"))) token.dropLast(1) else token
    }
}
