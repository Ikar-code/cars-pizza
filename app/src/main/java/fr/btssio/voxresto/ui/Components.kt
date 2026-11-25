package fr.btssio.voxresto.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.btssio.voxresto.data.TicketStatus

fun TicketStatus.label(): String = when (this) {
    TicketStatus.DRAFT -> "Brouillon"
    TicketStatus.CONFIRMED -> "En cours"
    TicketStatus.READY -> "Prêt"
    TicketStatus.CANCELLED -> "Annulé"
}

@Composable
fun StatusBadge(status: TicketStatus, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val (container, content) = when (status) {
        TicketStatus.CONFIRMED -> if (dark) Color(0xFF78350F) to Color(0xFFFEF3C7) else Color(0xFFFEF3C7) to Color(0xFF78350F)
        TicketStatus.READY -> if (dark) Color(0xFF14532D) to Color(0xFFDCFCE7) else Color(0xFFDCFCE7) to Color(0xFF14532D)
        TicketStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        TicketStatus.DRAFT -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
