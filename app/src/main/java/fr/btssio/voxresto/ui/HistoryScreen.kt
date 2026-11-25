package fr.btssio.voxresto.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Tous") })
            listOf(
                TicketStatus.CONFIRMED to "En cours",
                TicketStatus.READY to "Prêts",
                TicketStatus.CANCELLED to "Annulés",
            ).forEach { (status, label) ->
                FilterChip(
                    selected = filter == status.name,
                    onClick = { filter = status.name },
                    label = { Text(label) },
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
private fun TicketHistoryCard(details: TicketWithDetails, onReady: () -> Unit, onCancel: () -> Unit) {
    val ticket = details.ticket
    var expanded by rememberSaveable(ticket.id) { mutableStateOf(false) }

    ElevatedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ticket.number?.let { "Ticket n°$it" } ?: "Commande non validée",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(ticket.status)
            }
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = "${Format.dateTime(ticket.confirmedAt ?: ticket.createdAt)} · ${details.itemCount} article(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(Format.price(details.totalCents), fontWeight = FontWeight.Bold)
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    details.lines.sortedBy { it.id }.forEach { line ->
                        Row {
                            Text("${line.quantity} × ${line.name}", modifier = Modifier.weight(1f))
                            Text(Format.price(line.unitPriceCents * line.quantity))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text("Journal", style = MaterialTheme.typography.labelLarge)
                    details.events.sortedWith(compareBy({ it.timestamp }, { it.id })).forEach { event ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Icon(
                                imageVector = when (event.source) {
                                    InputSource.VOICE -> Icons.Filled.Mic
                                    InputSource.TEXT -> Icons.Filled.Keyboard
                                    InputSource.TOUCH -> Icons.Filled.TouchApp
                                },
                                contentDescription = event.source.name,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "  ${Format.time(event.timestamp)}  ${event.detail}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    if (ticket.status == TicketStatus.CONFIRMED) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        ) {
                            TextButton(onClick = onCancel) { Text("Annuler le ticket") }
                            Button(onClick = onReady) { Text("Marquer prêt") }
                        }
                    }
                }
            }
        }
    }
}
