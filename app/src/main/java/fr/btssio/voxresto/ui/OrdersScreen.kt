package fr.btssio.voxresto.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.btssio.voxresto.data.TicketStatus
import fr.btssio.voxresto.data.TicketWithDetails

private enum class OrdersFilter(val label: String) {
    ACTIVE("En cours"),
    HISTORY("Historique"),
}

@Composable
fun OrdersScreen(vm: OrdersViewModel) {
    val tickets by vm.tickets.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(OrdersFilter.ACTIVE) }
    val scheme = MaterialTheme.colorScheme

    val active = tickets.filter { it.ticket.status == TicketStatus.PENDING || it.ticket.status == TicketStatus.PREPARING }
    // F6 : ordre d'arrivée pour les commandes à préparer ; F10 : les plus récentes d'abord dans l'historique.
    val shown = when (filter) {
        OrdersFilter.ACTIVE -> active
        OrdersFilter.HISTORY -> tickets
            .filter { it.ticket.status == TicketStatus.COMPLETED || it.ticket.status == TicketStatus.CANCELLED }
            .sortedByDescending { it.ticket.createdAtMillis }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        ) {
            StatTile(
                count = active.count { it.ticket.status == TicketStatus.PENDING },
                label = "En attente",
                icon = Icons.Filled.Schedule,
                container = scheme.tertiaryContainer,
                content = scheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                count = active.count { it.ticket.status == TicketStatus.PREPARING },
                label = "En préparation",
                icon = Icons.Filled.LocalFireDepartment,
                container = scheme.primaryContainer,
                content = scheme.primary,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = scheme.primaryContainer,
                selectedLabelColor = scheme.onPrimaryContainer,
            )
            OrdersFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { filter = option },
                    label = { Text(option.label) },
                    colors = chipColors,
                )
            }
        }

        if (shown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (filter == OrdersFilter.ACTIVE) "Aucune commande à préparer." else "Aucune commande terminée.",
                    color = scheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shown, key = { it.ticket.id }) { details ->
                    TicketCard(
                        details = details,
                        onAdvance = { vm.advance(details.ticket.id) },
                        onCancel = { vm.cancel(details.ticket.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(
    count: Int,
    label: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    VoxCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .background(container, MaterialTheme.shapes.medium),
            ) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
            }
            Text("$count", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TicketCard(details: TicketWithDetails, onAdvance: () -> Unit, onCancel: () -> Unit) {
    val ticket = details.ticket
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable(ticket.id) { mutableStateOf(false) }
    val (statusContainer, statusContent) = statusColors(ticket.status)
    val active = ticket.status == TicketStatus.PENDING || ticket.status == TicketStatus.PREPARING

    VoxCard(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (ticket.status == TicketStatus.PREPARING) scheme.primary.copy(alpha = 0.35f) else scheme.outlineVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(statusContainer, MaterialTheme.shapes.medium),
            ) {
                if (ticket.status == TicketStatus.COMPLETED) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = statusContent)
                } else {
                    Text("#${ticket.shortNumber}", style = MaterialTheme.typography.titleSmall, color = statusContent)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text("Ticket n°${ticket.shortNumber}", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${Format.dateTime(ticket.createdAtMillis)} · ${details.itemCount} pizza(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            StatusBadge(ticket.status)
        }
        HorizontalDivider(color = scheme.outlineVariant)

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            details.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${item.quantity}×",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                        modifier = Modifier.width(32.dp),
                    )
                    Text(
                        text = item.pizzaName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = Format.price(item.subtotalCents),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        Surface(color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline("Total", modifier = Modifier.weight(1f))
                    Text(Format.price(ticket.totalCents), style = MaterialTheme.typography.titleLarge)
                }
                if (active) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        TextButton(onClick = onCancel) { Text("Annuler") }
                        val preparing = ticket.status == TicketStatus.PREPARING
                        Button(onClick = onAdvance, shape = MaterialTheme.shapes.medium) {
                            Icon(
                                imageVector = if (preparing) Icons.Filled.Check else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(if (preparing) "Terminée" else "Commencer", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }

        // Historique des changements d'état (table ticket_status_history) : visible en touchant la carte.
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(16.dp)) {
                Overline("Historique")
                details.history.forEach { change ->
                    Text(
                        text = "${Format.time(change.changedAtMillis)}  " +
                            (change.oldStatus?.let { "${it.label()} → " } ?: "") + change.newStatus.label(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
