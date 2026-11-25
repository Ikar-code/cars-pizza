package fr.btssio.voxresto.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.btssio.voxresto.data.TicketStatus

fun TicketStatus.label(): String = when (this) {
    TicketStatus.DRAFT -> "Brouillon"
    TicketStatus.CONFIRMED -> "En cours"
    TicketStatus.READY -> "Prêt"
    TicketStatus.CANCELLED -> "Annulé"
}

/** Couleurs (fond, texte) d'un statut : ambre = en cours, vert = prêt, rouge = annulé, comme sur la maquette. */
@Composable
fun statusColors(status: TicketStatus): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        TicketStatus.CONFIRMED -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        TicketStatus.READY -> scheme.secondaryContainer to scheme.onSecondaryContainer
        TicketStatus.CANCELLED -> scheme.errorContainer to scheme.onErrorContainer
        TicketStatus.DRAFT -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
}

@Composable
fun StatusBadge(status: TicketStatus, modifier: Modifier = Modifier) {
    val (container, content) = statusColors(status)
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(
            text = status.label().uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Petit titre en capitales espacées (« NOUVEAU TICKET », « TOTAL »...), très présent sur la maquette. */
@Composable
fun Overline(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp),
    )
}

/** Carte blanche à fine bordure, sans ombre : le style de toutes les cartes de la maquette. */
@Composable
fun VoxCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    val border = BorderStroke(1.dp, borderColor)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = colors,
            border = border,
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            colors = colors,
            border = border,
            content = content,
        )
    }
}
