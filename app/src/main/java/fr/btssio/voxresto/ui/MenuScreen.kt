package fr.btssio.voxresto.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Restaurant
import fr.btssio.voxresto.data.MenuItem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable
fun MenuScreen(vm: MenuViewModel) {
    val menu by vm.menu.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            menu.groupBy { it.category }.forEach { (category, dishes) ->
                item(key = "header-$category") {
                    Overline(
                        text = category,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                items(dishes, key = { it.id }) { dish ->
                    DishCard(
                        dish = dish,
                        onAvailableChange = { vm.setAvailable(dish.id, it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showDialog = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Ajouter un plat") },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (showDialog) {
        AddItemDialog(
            onDismiss = { showDialog = false },
            onSave = { name, category, cents, aliases, done ->
                vm.addItem(name, category, cents, aliases, done)
            },
        )
    }
}

/** Carte d'un plat (maquette « La carte ») : pastille d'icône, nom, prix, synonymes vocaux, disponibilité. */
@Composable
private fun DishCard(dish: MenuItem, onAvailableChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    VoxCard(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .alpha(if (dish.available) 1f else 0.6f),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(scheme.primaryContainer, MaterialTheme.shapes.medium),
            ) {
                Icon(
                    imageVector = Icons.Filled.Restaurant,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)) {
                Text(dish.name, style = MaterialTheme.typography.titleMedium)
                val aliases = dish.aliasList
                if (aliases.isNotEmpty()) {
                    Text(
                        text = "aussi : ${aliases.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (dish.available) scheme.secondary else scheme.outline, CircleShape),
                    )
                    Text(
                        text = if (dish.available) "Disponible" else "Indisponible",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (dish.available) scheme.secondary else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.price(dish.priceCents), style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = dish.available,
                    onCheckedChange = onAvailableChange,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun AddItemDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, priceCents: Int, aliases: String, done: (Boolean) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Plats") }
    var price by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val priceCents = price.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * 100).roundToInt() }
    val valid = name.isNotBlank() && category.isNotBlank() && priceCents != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau plat") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nom") },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { message -> @Composable { Text(message) } },
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Catégorie") },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Prix (€)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = aliases,
                    onValueChange = { aliases = it },
                    label = { Text("Synonymes vocaux (facultatif)") },
                    supportingText = { Text("Séparés par des virgules") },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(name, category, priceCents ?: 0, aliases) { ok ->
                        if (ok) onDismiss() else error = "Ce plat existe déjà."
                    }
                },
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
