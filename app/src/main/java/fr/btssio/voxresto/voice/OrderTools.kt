package fr.btssio.voxresto.voice

import org.json.JSONArray
import org.json.JSONObject

/**
 * Ce que Gemini a le droit de faire : cinq fonctions qui pilotent le ticket en cours.
 * Le modèle comprend la phrase de l'employé, mais c'est l'application qui modifie le ticket
 * et renvoie le résultat : le modèle ne peut ni inventer un prix ni valider sans appeler `validate_order`.
 */
object OrderTools {
    const val ADD = "add_pizzas"
    const val REMOVE = "remove_pizzas"
    const val READ = "read_order"
    const val VALIDATE = "validate_order"
    const val CANCEL = "cancel_order"

    /** Consignes du modèle. [menuLines] : une ligne par pizza disponible, ex. « - Reine (11,00 €) ». */
    fun systemPrompt(menuLines: List<String>): String = buildString {
        appendLine("Tu es l'assistant vocal d'une pizzeria. Un seul employé prépare les pizzas ET prend les commandes : il a les mains occupées et te dicte les commandes à voix haute.")
        appendLine()
        appendLine("Règles :")
        appendLine("- Parle uniquement en français, en phrases très courtes (une ou deux).")
        appendLine("- Tu ne modifies jamais le ticket toi-même : tu appelles toujours un outil.")
        appendLine("- Quand l'employé cite des pizzas, appelle add_pizzas UNE seule fois pour toute la phrase, puis confirme en quelques mots ce que l'outil a répondu.")
        appendLine("- « enlève », « retire » : remove_pizzas. « récap », « c'est combien » : read_order. « valide », « c'est tout » : validate_order. « annule la commande » : cancel_order.")
        appendLine("- Utilise exactement les noms du menu ci-dessous. Si un nom est ambigu ou inconnu, demande de préciser au lieu de deviner.")
        appendLine("- N'invente jamais une pizza, un prix ou un total : répète uniquement ce que renvoient les outils.")
        appendLine("- Ignore ce qui n'est pas un ordre de commande (bruit, discussion avec un client) : ne réponds pas dans ce cas.")
        appendLine()
        appendLine("Menu disponible :")
        menuLines.forEach { appendLine(it) }
    }

    fun declarations(): JSONArray = JSONArray().put(
        JSONObject().put(
            "functionDeclarations",
            JSONArray()
                .put(
                    function(
                        ADD,
                        "Ajoute des pizzas au ticket en cours. À appeler dès que l'employé dicte des pizzas.",
                        itemsParameter("Nombre de pizzas ; 1 si l'employé n'en précise pas."),
                    )
                )
                .put(
                    function(
                        REMOVE,
                        "Retire des pizzas du ticket en cours. Sans quantité, retire toute la ligne.",
                        itemsParameter("Nombre de pizzas à retirer ; à omettre pour tout retirer."),
                    )
                )
                .put(function(READ, "Lit le contenu et le total du ticket en cours."))
                .put(function(VALIDATE, "Valide et enregistre le ticket en cours (« c'est tout », « valide »)."))
                .put(function(CANCEL, "Annule et efface le ticket en cours."))
        )
    )

    // BLOCKING : le modèle attend le résultat de l'outil avant de parler, donc sa réponse
    // reflète ce qui a réellement été fait (pizza indisponible, total, numéro de ticket...).
    private fun function(name: String, description: String, parameters: JSONObject? = null): JSONObject =
        JSONObject()
            .put("name", name)
            .put("description", description)
            .put("behavior", "BLOCKING")
            .apply { if (parameters != null) put("parameters", parameters) }

    private fun itemsParameter(quantityDescription: String): JSONObject {
        val item = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put(
                        "pizza",
                        JSONObject().put("type", "STRING").put("description", "Nom de la pizza, tel qu'au menu."),
                    )
                    .put("quantity", JSONObject().put("type", "INTEGER").put("description", quantityDescription)),
            )
            .put("required", JSONArray().put("pizza"))
        return JSONObject()
            .put("type", "OBJECT")
            .put("properties", JSONObject().put("items", JSONObject().put("type", "ARRAY").put("items", item)))
            .put("required", JSONArray().put("items"))
    }
}
