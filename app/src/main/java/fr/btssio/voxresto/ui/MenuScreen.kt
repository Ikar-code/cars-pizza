package fr.btssio.voxresto.ui

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ListItem
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
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(dishes, key = { it.id }) { dish ->
                    ListItem(
                        modifier = Modifier.alpha(if (dish.available) 1f else 0.5f),
                        headlineContent = { Text(dish.name) },
                        supportingContent = {
                            val aliases = dish.aliasList
                            Text(
                                Format.price(dish.priceCents) +
                                    if (aliases.isEmpty()) "" else "  ·  aussi : ${aliases.joinToString(", ")}"
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = dish.available,
                                onCheckedChange = { vm.setAvailable(dish.id, it) },
                            )
                        },
                    )
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { showDialog = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Ajouter un plat") },
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
