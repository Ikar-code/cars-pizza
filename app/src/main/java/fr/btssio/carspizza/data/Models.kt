package fr.btssio.carspizza.data

/** Une pizza de la carte (table `pizza`). Prix en centimes. */
data class Pizza(
    val id: Long,
    val name: String,
    val description: String?,
    val priceCents: Int,
    val preparationMinutes: Int,
    val isAvailable: Boolean,
)

/** Champs modifiables d'une pizza (création ou modification depuis l'écran « Menu »). */
data class PizzaInput(
    val name: String,
    val description: String?,
    val priceCents: Int,
    val preparationMinutes: Int,
)

/** États d'un ticket enregistré : exactement les valeurs autorisées par la colonne `ticket.status`. */
enum class TicketStatus { PENDING, PREPARING, COMPLETED, CANCELLED }

/** Ticket enregistré (table `ticket`). */
data class Ticket(
    val id: Long,
    /** Ex. « 20261125-003 » : date du jour + rang dans la journée, unique. */
    val number: String,
    val createdAtMillis: Long,
    val status: TicketStatus,
    val totalCents: Int,
) {
    /** « 20261125-003 » → 3 : numéro court affiché sur la pastille et annoncé à voix haute. */
    val shortNumber: Int
        get() = number.substringAfterLast('-').toIntOrNull() ?: 0
}

/**
 * Ligne de ticket (table `ticket_item`). Le nom et le prix sont copiés au moment de la commande :
 * modifier le menu plus tard ne réécrit pas les anciens tickets.
 */
data class TicketItem(
    val id: Long,
    val ticketId: Long,
    val pizzaId: Long?,
    val pizzaName: String,
    val quantity: Int,
    val unitPriceCents: Int,
    val subtotalCents: Int,
)

/** Une ligne de `ticket_status_history`. [oldStatus] est nul à la création du ticket. */
data class StatusChange(
    val id: Long,
    val ticketId: Long,
    val oldStatus: TicketStatus?,
    val newStatus: TicketStatus,
    val changedAtMillis: Long,
)

data class TicketWithDetails(
    val ticket: Ticket,
    val items: List<TicketItem>,
    val history: List<StatusChange>,
) {
    val itemCount: Int
        get() = items.sumOf { it.quantity }
}

/** Ligne de la commande en cours de dictée (pas encore enregistrée en base). */
data class DraftLine(
    val pizzaId: Long,
    val name: String,
    val unitPriceCents: Int,
    val quantity: Int,
)

/**
 * Commande en cours de dictée. Elle vit en mémoire et n'est écrite en base qu'à la validation
 * (cahier des charges §7) : un brouillon abandonné ne laisse aucune trace.
 */
data class DraftOrder(val lines: List<DraftLine> = emptyList()) {
    val totalCents: Int
        get() = lines.sumOf { it.unitPriceCents * it.quantity }
    val itemCount: Int
        get() = lines.sumOf { it.quantity }
    val isEmpty: Boolean
        get() = lines.isEmpty()
}
