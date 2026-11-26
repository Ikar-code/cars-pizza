package fr.btssio.carspizza.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import fr.btssio.carspizza.data.Pizza
import fr.btssio.carspizza.data.PizzaInput
import kotlin.math.roundToInt

@Composable
fun MenuScreen(vm: MenuViewModel) {
    val menu by vm.menu.collectAsStateWithLifecycle()
    // null = fenêtre fermée ; PizzaForm(null) = nouvelle pizza ; PizzaForm(pizza) = modification.
    var editing by remember { mutableStateOf<PizzaForm?>(null) }
    var deleting by remember { mutableStateOf<Pizza?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)) {
            items(menu, key = { it.id }) { pizza ->
                PizzaCard(
                    pizza = pizza,
                    onAvailableChange = { vm.setAvailable(pizza.id, it) },
                    onEdit = { editing = PizzaForm(pizza) },
                    onDelete = { deleting = pizza },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editing = PizzaForm(null) },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Ajouter une pizza") },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    editing?.let { form ->
        PizzaDialog(
            pizza = form.pizza,
            onDismiss = { editing = null },
            onSave = { input, done -> vm.save(form.pizza?.id, input, done) },
        )
    }

    deleting?.let { pizza ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Supprimer « ${pizza.name} » ?") },
            text = { Text("Les anciens tickets gardent son nom et son prix. Pour la retirer temporairement de la carte, désactivez-la plutôt.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(pizza.id); deleting = null }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
}

private data class PizzaForm(val pizza: Pizza?)

@Composable
private fun PizzaCard(
    pizza: Pizza,
    onAvailableChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    CarsCard(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)
                .alpha(if (pizza.isAvailable) 1f else 0.6f),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(scheme.primaryContainer, MaterialTheme.shapes.medium),
            ) {
                Icon(Icons.Filled.LocalPizza, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(22.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(pizza.name, style = MaterialTheme.typography.titleMedium)
                pizza.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (pizza.isAvailable) scheme.secondary else scheme.outline, CircleShape),
                    )
                    Text(
                        text = (if (pizza.isAvailable) "Disponible" else "Indisponible") +
                            " · ${pizza.preparationMinutes} min",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (pizza.isAvailable) scheme.secondary else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.price(pizza.priceCents), style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = pizza.isAvailable,
                    onCheckedChange = onAvailableChange,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp),
                )
            }
            Column {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Modifier ${pizza.name}") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer ${pizza.name}") }
            }
        }
    }
}

@Composable
private fun PizzaDialog(
    pizza: Pizza?,
    onDismiss: () -> Unit,
    onSave: (input: PizzaInput, done: (Boolean) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(pizza?.name.orEmpty()) }
    var description by remember { mutableStateOf(pizza?.description.orEmpty()) }
    var price by remember { mutableStateOf(pizza?.let { "%.2f".format(java.util.Locale.FRANCE, it.priceCents / 100.0) }.orEmpty()) }
    var minutes by remember { mutableStateOf((pizza?.preparationMinutes ?: 15).toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    val priceCents = price.replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * 100).roundToInt() }
    val prepMinutes = minutes.toIntOrNull()?.takeIf { it > 0 }
    val valid = name.isNotBlank() && priceCents != null && prepMinutes != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pizza == null) "Nouvelle pizza" else "Modifier la pizza") },
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
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Ingrédients (facultatif)") },
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
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit) },
                    label = { Text("Préparation (minutes)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val input = PizzaInput(name, description.ifBlank { null }, priceCents ?: 0, prepMinutes ?: 15)
                    onSave(input) { ok -> if (ok) onDismiss() else error = "Une pizza porte déjà ce nom." }
                },
            ) { Text(if (pizza == null) "Ajouter" else "Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
