package fr.btssio.voxresto.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import fr.btssio.voxresto.data.InputSource
import fr.btssio.voxresto.data.TicketStatus
import fr.btssio.voxresto.data.TicketWithDetails

@Composable
fun HistoryScreen(vm: HistoryViewModel) {
    val history by vm.history.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = history.filter { filter == null || it.ticket.status.name == filter }
    val scheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        // Deux compteurs en tête d'écran, comme « En attente / En préparation » sur la maquette.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        ) {
            StatTile(
                count = history.count { it.ticket.status == TicketStatus.CONFIRMED },
                label = "En cours",
                icon = Icons.Filled.Schedule,
                container = scheme.tertiaryContainer,
                content = scheme.tertiary,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                count = history.count { it.ticket.status == TicketStatus.READY },
                label = "Prêts",
                icon = Icons.Filled.Check,
                container = scheme.secondaryContainer,
                content = scheme.secondary,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = scheme.primaryContainer,
                selectedLabelColor = scheme.onPrimaryContainer,
            )
            FilterChip(
                selected = filter == null,
                onClick = { filter = null },
                label = { Text("Tous") },
                colors = chipColors,
            )
            listOf(
                TicketStatus.CONFIRMED to "En cours",
                TicketStatus.READY to "Prêts",
                TicketStatus.CANCELLED to "Annulés",
            ).forEach { (status, label) ->
                FilterChip(
                    selected = filter == status.name,
                    onClick = { filter = status.name },
                    label = { Text(label) },
                    colors = chipColors,
                )
            }
        }

        if (shown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Aucune commande pour le moment.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(shown, key = { it.ticket.id }) { details ->
                    TicketHistoryCard(
                        details = details,
                        onReady = { vm.markReady(details.ticket.id) },
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
private fun TicketHistoryCard(details: TicketWithDetails, onReady: () -> Unit, onCancel: () -> Unit) {
    val ticket = details.ticket
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable(ticket.id) { mutableStateOf(false) }
    val (statusContainer, statusContent) = statusColors(ticket.status)
    val active = ticket.status == TicketStatus.CONFIRMED

    VoxCard(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        // Les tickets en cours ont un liseré rouge, comme « En préparation » sur la maquette.
        borderColor = if (active) scheme.primary.copy(alpha = 0.35f) else scheme.outlineVariant,
    ) {
        // En-tête : pastille de numéro, titre, statut.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(statusContainer, MaterialTheme.shapes.medium),
            ) {
                if (ticket.status == TicketStatus.READY) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = statusContent)
                } else {
                    Text(
                        text = ticket.number?.let { "#$it" } ?: "…",
                        style = MaterialTheme.typography.titleSmall,
                        color = statusContent,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = ticket.number?.let { "Ticket n°$it" } ?: "Commande non validée",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${Format.dateTime(ticket.confirmedAt ?: ticket.createdAt)} · ${details.itemCount} article(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            StatusBadge(ticket.status)
        }
        HorizontalDivider(color = scheme.outlineVariant)

        // Lignes du ticket : quantité en rouge, nom, montant.
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            details.lines.sortedBy { it.id }.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${line.quantity}×",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.primary,
                        modifier = Modifier.width(32.dp),
                    )
                    Text(
                        text = line.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = Format.price(line.unitPriceCents * line.quantity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Pied de carte : total et, pour un ticket en cours, les actions.
        Surface(color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Overline("Total", modifier = Modifier.weight(1f))
                    Text(Format.price(details.totalCents), style = MaterialTheme.typography.titleLarge)
                }
                if (active) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        TextButton(onClick = onCancel) { Text("Annuler le ticket") }
                        Button(
                            onClick = onReady,
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = scheme.secondary,
                                contentColor = scheme.onSecondary,
                            ),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Marquer prêt", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }

        // Journal d'audit : visible en touchant la carte.
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(16.dp)) {
                Overline("Journal")
                details.events.sortedWith(compareBy({ it.timestamp }, { it.id })).forEach { event ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Icon(
                            imageVector = when (event.source) {
                                InputSource.VOICE -> Icons.Filled.Mic
                                InputSource.TEXT -> Icons.Filled.Keyboard
                                InputSource.TOUCH -> Icons.Filled.TouchApp
                            },
                            contentDescription = event.source.name,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "  ${Format.time(event.timestamp)}  ${event.detail}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
