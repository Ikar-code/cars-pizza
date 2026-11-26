package fr.btssio.voxresto.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.btssio.voxresto.data.DraftOrder
import fr.btssio.voxresto.data.Pizza

@Composable
fun OrderScreen(vm: OrderViewModel) {
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pizzas by vm.availablePizzas.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf("") }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening() else vm.onPermissionDenied()
    }

    fun onMicClick() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        when {
            ui.voiceState == VoiceState.LISTENING || ui.voiceState == VoiceState.CONNECTING -> vm.stopListening()
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
            listening = ui.voiceState == VoiceState.LISTENING,
            onPlus = vm::increment,
            onMinus = vm::decrement,
            onConfirm = vm::confirm,
            onCancel = vm::cancel,
            onAddManually = { showPicker = true },
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
                    !ui.speechAvailable -> "Reconnaissance vocale indisponible : saisissez à la main."
                    ui.voiceState == VoiceState.CONNECTING -> "Connexion à Gemini…"
                    ui.voiceState == VoiceState.LISTENING && ui.engine == VoiceEngine.GEMINI ->
                        "Gemini vous écoute (${ui.micLabel.ifEmpty { "micro" }})"
                    ui.voiceState == VoiceState.LISTENING -> "Je vous écoute…"
                    ui.voiceState == VoiceState.PROCESSING -> "Analyse en cours…"
                    ui.geminiConfigured && ui.useGemini -> "Appuyez sur le micro (Gemini Live)"
                    else -> "Appuyez sur le micro (reconnaissance Android)"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Réglages de la voix")
            }
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
                shape = MaterialTheme.shapes.medium,
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

    if (showPicker) {
        PizzaPickerDialog(
            pizzas = pizzas,
            onPick = vm::addPizza,
            onDismiss = { showPicker = false },
        )
    }
    if (showSettings) {
        SettingsDialog(
            initialKey = "",
            useGemini = ui.useGemini,
            onSave = { key, use -> vm.saveSettings(key, use); showSettings = false },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun PizzaPickerDialog(pizzas: List<Pizza>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une pizza") },
        text = {
            LazyColumn {
                items(pizzas, key = { it.id }) { pizza ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pizza.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                Format.price(pizza.priceCents),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledIconButton(onClick = { onPick(pizza.id) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Ajouter ${pizza.name}")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Terminé") } },
    )
}

@Composable
private fun SettingsDialog(
    initialKey: String,
    useGemini: Boolean,
    onSave: (apiKey: String, useGemini: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf(initialKey) }
    var use by remember { mutableStateOf(useGemini) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voix : Gemini Live") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Utiliser Gemini Live", modifier = Modifier.weight(1f))
                    Switch(checked = use, onCheckedChange = { use = it })
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Clé API Gemini") },
                    placeholder = { Text("Laisser vide pour conserver la clé actuelle") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "Sans clé, l'application utilise la reconnaissance vocale d'Android. " +
                        "La clé reste sur cet appareil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key, use) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun MicButton(ui: OrderUiState, onClick: () -> Unit) {
    val listening = ui.voiceState == VoiceState.LISTENING || ui.voiceState == VoiceState.CONNECTING
    val scale by animateFloatAsState(
        targetValue = if (listening) 1f + ui.level * 0.35f else 1f,
        label = "mic-pulse",
    )
    // Halo qui bat doucement pendant l'écoute (anneaux de la maquette).
    val halo by rememberInfiniteTransition(label = "mic-halo").animateFloat(
        initialValue = 0.82f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 1450), repeatMode = RepeatMode.Restart),
        label = "mic-halo-scale",
    )
    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(halo)
                    .alpha((1.25f - halo) * 2f)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
            )
        }
        FilledIconButton(
            onClick = onClick,
            enabled = ui.speechAvailable && ui.voiceState != VoiceState.PROCESSING,
            modifier = Modifier
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
}

@Composable
private fun TicketCard(
    draft: DraftOrder,
    listening: Boolean,
    onPlus: (Long) -> Unit,
    onMinus: (Long) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onAddManually: () -> Unit,
) {
    VoxCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Overline("Nouveau ticket", color = MaterialTheme.colorScheme.primary)
            Text("Commande en cours", style = MaterialTheme.typography.titleLarge)
            val lines = draft.lines

            if (lines.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                ) {
                    Text(
                        text = if (listening) "Je vous écoute…" else "Dictez la commande",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (listening) "Parlez clairement, puis marquez une courte pause."
                        else "Par exemple : « Deux margherita et une reine »",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    OutlinedButton(onClick = onAddManually, modifier = Modifier.padding(top = 12.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Ajouter à la main", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                return@Column
            }

            Column(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                lines.forEachIndexed { index, line ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(line.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${Format.price(line.unitPriceCents)} l'unité",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        QuantityStepper(
                            quantity = line.quantity,
                            name = line.name,
                            onMinus = { onMinus(line.pizzaId) },
                            onPlus = { onPlus(line.pizzaId) },
                        )
                        Text(
                            text = Format.price(line.unitPriceCents * line.quantity),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(72.dp),
                        )
                    }
                }
            }

            // Barre « Total à payer » sombre, comme sur la maquette.
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = "Total",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .weight(1f)
                            .alpha(0.7f),
                    )
                    Text(Format.price(draft.totalCents), style = MaterialTheme.typography.titleLarge)
                }
            }
            Text(
                text = "Dites « valide la commande » ou utilisez le bouton.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                ) { Text("Annuler") }
                OutlinedButton(
                    onClick = onAddManually,
                    shape = MaterialTheme.shapes.medium,
                ) { Icon(Icons.Filled.Add, contentDescription = "Ajouter une pizza à la main") }
                Button(
                    onClick = onConfirm,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1.7f),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Valider le ticket", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Sélecteur de quantité de la maquette : bouton − gris, nombre, bouton + rouge dans un cadre arrondi. */
@Composable
private fun QuantityStepper(quantity: Int, name: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinus, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = "Retirer un $name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "$quantity",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(24.dp),
            )
            IconButton(onClick = onPlus, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Ajouter un $name",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
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
