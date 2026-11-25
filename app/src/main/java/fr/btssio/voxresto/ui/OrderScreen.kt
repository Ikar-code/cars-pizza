package fr.btssio.voxresto.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.btssio.voxresto.data.TicketWithDetails

@Composable
fun OrderScreen(vm: OrderViewModel) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening() else vm.onPermissionDenied()
    }

    fun onMicClick() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        when {
            ui.voiceState == VoiceState.LISTENING -> vm.stopListening()
            granted -> vm.startListening()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun send() {
        vm.submitText(text)
        text = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        TicketCard(
            draft = draft,
            onPlus = vm::increment,
            onMinus = vm::decrement,
            onConfirm = vm::confirm,
            onCancel = vm::cancel,
        )

        Transcript(ui = ui, modifier = Modifier.weight(1f))

        ui.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    !ui.speechAvailable -> "Reconnaissance vocale indisponible : utilisez le clavier."
                    ui.voiceState == VoiceState.LISTENING -> "Je vous écoute…"
                    ui.voiceState == VoiceState.PROCESSING -> "Analyse en cours…"
                    else -> "Appuyez sur le micro pour parler"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = vm::toggleSpeakReplies) {
                Icon(
                    imageVector = if (ui.speakReplies) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = if (ui.speakReplies) "Couper la voix" else "Activer la voix",
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ou écrivez votre commande") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            IconButton(onClick = { send() }, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Envoyer")
            }
            MicButton(ui = ui, onClick = { onMicClick() })
        }
    }
}

@Composable
private fun MicButton(ui: OrderUiState, onClick: () -> Unit) {
    val listening = ui.voiceState == VoiceState.LISTENING
    val scale by animateFloatAsState(
        targetValue = if (listening) 1f + ui.level * 0.35f else 1f,
        label = "mic-pulse",
    )
    FilledIconButton(
        onClick = onClick,
        enabled = ui.speechAvailable && ui.voiceState != VoiceState.PROCESSING,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(60.dp)
            .scale(scale),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = if (listening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (listening) "Arrêter l'écoute" else "Parler",
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun TicketCard(
    draft: TicketWithDetails?,
    onPlus: (Long) -> Unit,
    onMinus: (Long) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Commande en cours", style = MaterialTheme.typography.titleMedium)
            val lines = draft?.lines.orEmpty().sortedBy { it.id }

            if (lines.isEmpty()) {
                Text(
                    text = "Aucun article pour le moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                return@Column
            }

            Column(
                modifier = Modifier
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                lines.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(line.name, modifier = Modifier.weight(1f))
                        IconButton(onClick = { line.menuItemId?.let(onMinus) }) {
                            Icon(Icons.Filled.Remove, contentDescription = "Retirer un ${line.name}")
                        }
                        Text("${line.quantity}", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { line.menuItemId?.let(onPlus) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Ajouter un ${line.name}")
                        }
                        Text(
                            text = Format.price(line.unitPriceCents * line.quantity),
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(76.dp),
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row {
                Text("Total", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(Format.price(draft?.totalCents ?: 0), fontWeight = FontWeight.Bold)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                OutlinedButton(onClick = onCancel) { Text("Annuler") }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Valider la commande") }
            }
        }
    }
}

@Composable
private fun Transcript(ui: OrderUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val hasPartial = ui.partialText.isNotEmpty()
    val total = ui.transcript.size + if (hasPartial) 1 else 0

    LaunchedEffect(total) {
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(ui.transcript, key = { it.id }) { line -> Bubble(line.text, fromUser = line.fromUser) }
        if (hasPartial) {
            item(key = "partial") { Bubble(ui.partialText + " …", fromUser = true, faded = true) }
        }
    }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean, faded: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (fromUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .alpha(if (faded) 0.6f else 1f),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
